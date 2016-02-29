/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

/*
 * Authors: Vivek Kumar, Daniel Frampton
 */

package org.jikesrvm.scheduler;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.ia32.RegisterConstants;
import org.jikesrvm.mm.mminterface.GCMapIterator;
import org.jikesrvm.mm.mminterface.GCMapIteratorGroup;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.WordArray;
import org.jikesrvm.ArchitectureSpecific.Registers;


@NonMoving
@Uninterruptible
public final class StackFrameCopier implements SizeConstants {

	/***********************************************************************
	 *
	 * Instance variables
	 */
	private final GCMapIteratorGroup iteratorGroup = new GCMapIteratorGroup();
	@Untraced
	private GCMapIterator iterator;

	// Return Barrier Implementation
	@Untraced
	private final WordArray gprs_current = MemoryManager.newNonMovingWordArray(RegisterConstants.NUM_GPRS);

	/***********************************************************************
	 *
	 * Thread scanning
	 */

	private void reinitializeStackIteratorGroup(RVMThread scanThread) {
		if(RVMThread.wsDebugTrace) {
			debugStackFrameProcessing = true;
			debugScanFp = Address.zero();
			debugScanIp = Address.zero();
		}
		for(int i=0; i<RegisterConstants.NUM_GPRS; i++) gprs_current.set(i, Word.zero());
		Address gprs = Magic.objectAsAddress(gprs_current);
		/* reinitialize the stack iterator group */
		iteratorGroup.newStackWalk(scanThread, gprs);
	}
	
	/**
	 * A more general interface to thread scanning, which permits the
	 * scanning of stack segments which are dislocated from the thread
	 * structure.
	 *
	 * @param thread The thread to be scanned
	 * @param gprs The general purpose registers associated with the
	 * stack being scanned (normally extracted from the thread).
	 * @param topFrame The top frame of the stack being scanned, or zero
	 * if this is to be inferred from the thread (normally the case).
	 */
	protected void copyStack(Address fp, Address ip, RVMThread scanThread, RVMThread toThread, 
			Registers toRegs, Offset stealFPOffset, int count, Offset newStackOffset, Address scanTop) {
		
		/* reinitialize the stack iterator group */
		reinitializeStackIteratorGroup(scanThread);
		
		/* Skip over the frames we do not want to copy */
		Address prevFp = Address.zero();
		while (true) {
			Offset fpOffset = scanTop.diff(fp);
			if (fpOffset.EQ(stealFPOffset)) {
				if (toRegs != null) {
					//toRegs.copyFrom(regs);
					// TODO this doesn't deal with FPRs.
					iteratorGroup.copyRegisterValues(Magic.objectAsAddress(toRegs.gprs));
					toRegs.setInnermost(ip, fp);
				}
				break;
			}
			if (Magic.getCallerFramePointer(fp).EQ(ArchitectureSpecific.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP)) {
				VM.sysWriteln("Requested fp: ", stealFPOffset);
				VM.sysWriteln("Should be FP: ", scanTop.minus(stealFPOffset));
				VM.sysWriteln("Scan Top: ", scanTop);
				VM.sysWriteln("Offset:   ", newStackOffset);
				RVMThread.dumpStack(scanThread.getContextRegisters().getInnermostInstructionAddress(), scanThread.getContextRegisters().getInnermostFramePointer());
				VM.sysFail("Could not find requested fp");
			}
			processFrame(newStackOffset, ip, fp);
			ip = RVMThread.getReturnAddress(fp);
			prevFp = fp;
			fp = Magic.getCallerFramePointer(fp);
		}

		// Copy the initial part of this frame
		Memory.alignedWordCopy(prevFp.plus(newStackOffset).plus(BYTES_IN_ADDRESS), prevFp.plus(BYTES_IN_ADDRESS), fp.diff(prevFp).toInt() - BYTES_IN_ADDRESS);
		Magic.setCallerFramePointer(prevFp.plus(newStackOffset), fp.plus(newStackOffset));
		while(true) {
			Address nextFp = Magic.getCallerFramePointer(fp);

			if (nextFp.EQ(ArchitectureSpecific.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP) || count == 0) {
				Magic.setCallerFramePointer(fp.plus(newStackOffset), ArchitectureSpecific.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP);
				prevFp.plus(newStackOffset).store(Address.zero(), Offset.fromIntSignExtend(ArchitectureSpecific.StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET));
				fp.plus(newStackOffset).store(ArchitectureSpecific.StackframeLayoutConstants.INVISIBLE_METHOD_ID, Offset.fromIntSignExtend(ArchitectureSpecific.StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET));
				break;
			}

			// Copy the rest of this frame
			Memory.alignedWordCopy(fp.plus(newStackOffset).plus(BYTES_IN_ADDRESS), fp.plus(BYTES_IN_ADDRESS), nextFp.diff(prevFp).toInt() - BYTES_IN_ADDRESS);

			// Fix up the frame pointer
			Magic.setCallerFramePointer(fp.plus(newStackOffset), nextFp.plus(newStackOffset));

			// Process the frame
			processFrameAndUpdateThread(scanThread, toThread, newStackOffset, ip, fp);
			count--;

			// Move on to the next frame
			ip = RVMThread.getReturnAddress(fp);
			prevFp = fp;
			fp = nextFp;
		}

		if(false) {
			int stolenNVMap = 0;
			int seenNV = 0;
			int targetNV = 0;
			for (int i=0; i< ArchitectureSpecific.ArchConstants.NONVOLATILE_GPRS.length; i++) {
				targetNV |= (1 << ArchitectureSpecific.ArchConstants.NONVOLATILE_GPRS[i].value());
			}

			while (Magic.getCallerFramePointer(fp).NE(ArchitectureSpecific.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP)) {
				/* set up iterators etc, and skip the frame if appropriate */
				int compiledMethodId = Magic.getCompiledMethodID(fp);
				if (compiledMethodId != ArchitectureSpecific.ArchConstants.INVISIBLE_METHOD_ID) {
					/* establish the compiled method */
					CompiledMethod compiledMethod = CompiledMethods.getCompiledMethod(compiledMethodId);

					/* get the code associated with this frame */
					Offset offset = compiledMethod.getInstructionOffset(ip);

					/* initialize MapIterator for this frame */
					iterator = iteratorGroup.selectIterator(compiledMethod);
					iterator.setupIterator(compiledMethod, offset, fp);

					int nvObjectMap = iterator.getNVObjectMap();
					int nvSaved = iterator.getNVRegistersSaved();
					iterator.cleanupPointers();

					stolenNVMap |= (~seenNV & nvObjectMap);
					seenNV |= nvSaved | nvObjectMap;

					if (seenNV == targetNV) {
						break;
					}
				}
				// Move on to the next frame
				ip = RVMThread.getReturnAddress(fp);
				fp = Magic.getCallerFramePointer(fp);
			}
			toThread.wsSpecialNVMap = stolenNVMap;
			for(int i=0; i < ArchitectureSpecific.ArchConstants.NONVOLATILE_GPRS.length; i++) {
				int reg = ArchitectureSpecific.ArchConstants.NONVOLATILE_GPRS[i].value();
				if ((toThread.wsSpecialNVMap & (1 << reg)) != 0) {
					Address refaddr = iteratorGroup.getRegisterLocation(reg);
					if (Magic.addressAsObject(refaddr.loadAddress()) == scanThread) {
						refaddr.store(Magic.objectAsAddress(toThread), newStackOffset);
					}
				}
			}
		}
		debugStackFrameProcessing = false;
	}

	protected void processFrame(Offset stackOffset, Address ip, Address fp) {
		processFrameAndUpdateThread(null, null, stackOffset, ip, fp);
	}

	protected Address debugScanFp = Address.zero();
	protected Address debugScanIp = Address.zero();
	protected boolean debugStackFrameProcessing = false;
	
	private void processFrameAndUpdateThread(RVMThread oldThread, RVMThread newThread, Offset stackOffset, Address ip, Address fp) {
		if(RVMThread.wsDebugTrace) {
			debugScanFp = fp;
			debugScanIp = ip;
		}
		/* set up iterators etc, and skip the frame if appropriate */
		int compiledMethodId = Magic.getCompiledMethodID(fp);
		if (compiledMethodId != ArchitectureSpecific.ArchConstants.INVISIBLE_METHOD_ID) {
			/* establish the compiled method */
			CompiledMethod compiledMethod = CompiledMethods.getCompiledMethod(compiledMethodId);

			/* get the code associated with this frame */
			Offset offset = compiledMethod.getInstructionOffset(ip);

			/* initialize MapIterator for this frame */
			if(RVMThread.wsDebugTrace) {
				VM.sysWriteln(RVMThread.getCurrentThread().getId(),": starting processing frame ", fp);
			}
			iterator = iteratorGroup.selectIterator(compiledMethod);
			iterator.setupIterator(compiledMethod, offset, fp);

			/* scan the frame for object pointers */
			for (Address refaddr = iterator.getNextReferenceAddress(); !refaddr.isZero(); refaddr = iterator.getNextReferenceAddress()) {
				if (oldThread != null && Magic.addressAsObject(refaddr.loadAddress()) == oldThread) {
					refaddr.store(Magic.objectAsAddress(newThread), stackOffset);
				}
			}

			iterator.cleanupPointers();
		}
	}
}
