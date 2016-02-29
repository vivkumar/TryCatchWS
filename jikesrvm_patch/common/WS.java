
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
package org.jikesrvm.scheduler;

/*
 * Authors: Vivek Kumar, Daniel Frampton
 */

import static org.jikesrvm.runtime.SysCall.sysCall;

import java.util.Iterator;
import java.util.Random;

import org.jikesrvm.VM;
import org.jikesrvm.ArchitectureSpecific.StackframeLayoutConstants;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.pragma.UnpreemptibleNoWarn;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.mmtk.utility.statistics.EventCounter;

/**
 * Work stealing support class
 */
@Uninterruptible
public class WS {
	private static final int MAX_WORKERS = 32;

	private static boolean initialized = false;
	protected static volatile int numWorkers = 0;
	public static int wsProcs = 1; 
	private static volatile boolean terminate = false;
	protected static int[] workers = null;
	public static boolean stats = false;

	public static boolean wsInitialized() {
		return initialized;
	}

	/*
	 * In a big project (eg. JMetal) a work-stealing method
	 * may be called from several places i.e. several other methods.
	 * If there is a case where a method foo() calls some work-stealing
	 * method under a synchronized block, then there is no meaning of performing 
	 * work-stealing from this block. In such case it may also lead to deadlock
	 * To avoid this, we temporarily switch OFF the work-stealing on current thread
	 * if its calling work-stealing inside a synchronized block. 
	 */
	@Inline
	public static void pauseStealOnThread() {
		final RVMThread me = RVMThread.getCurrentThread();
		if(me.wsThread) {
			if(me.wsSynchronizationLock == 0) {
				me.wsLock().lockNoHandshake();
				if(!me.workstealingInProgress) {
					me.workstealingInProgress = true;
					me.wsSynchronizationLock++;					
				}
				me.wsLock().unlock();
			}
		}
	}

	// Switch ON work-stealing once the synchronized block
	// is done
	@Inline
	public static void resumeStealOnThread() {
		final RVMThread me = RVMThread.getCurrentThread();
		if(me.wsThread) {
			if(me.wsSynchronizationLock > 0) {
				me.wsSynchronizationLock--;
				if(me.wsSynchronizationLock == 0) {
					me.wsLock().lockNoHandshake();
					me.workstealingInProgress = false;
					me.wsLock().unlock();
				}
			}
		}
	}

	// Thread pinning only
	public static boolean pinLog = false;

	private final static EventCounter wsSuccessStealCycles = new EventCounter("wsSuccessStealCycles", true, true);
	private final static EventCounter wsFailedStealCycles = new EventCounter("wsFailedStealCycles", true, true);
	private final static EventCounter wsBarrierCycles = new EventCounter("wsBarrierCycles", true, true);
	private final static EventCounter wsThiefInstalledRBarriers = new EventCounter("wsThiefInstalledRBarriers", true, true);
	private final static EventCounter wsPreInstalledRBarriers = new EventCounter("wsPreInstalledRBarriers", true, true);
	private final static EventCounter wsFailedSteals = new EventCounter("wsFailedSteals", true, true);
	private final static EventCounter wsThreads = new EventCounter("wsThreads", true, true);
	private final static EventCounter wsTasksPushed = new EventCounter("wsTasksPushed", true, true);
	private final static EventCounter wsSuccessSteals = new EventCounter("wsSuccessSteals", true, true);
	
	private final static EventCounter wsTasksEQ2 = RVMThread.createContinuationDistribution ? new EventCounter("wsTasksEQ2", true, true) : null; 
	private final static EventCounter wsTasksLE4 = RVMThread.createContinuationDistribution ? new EventCounter("wsTasksLE4", true, true) : null;
	private final static EventCounter wsTasksGT4 = RVMThread.createContinuationDistribution ? new EventCounter("wsTasksGT4", true, true) : null;

	public static void updateStealRatioExternally(int steals, int pushes) {
		wsSuccessSteals.wsInc(steals);
		wsTasksPushed.wsInc(pushes);
		RVMThread.pin_core_index = 0;
	}
	
	public static void launchedFromX10(int procs) {
		wsProcs = procs;
		RVMThread.autogenWSThread = true;
		RVMThread.wsThreadsLaunched = true;
	}
	
	@Interruptible
	public synchronized static void register() {
		final RVMThread me = RVMThread.getCurrentThread();
		me.wsThread = true;
		me.workstealingInProgress = false;
		me.perfEventGroup = RVMThread.PERF_EVENT_GROUP_WS;
		if (!initialized) {
			initialized = true;
			workers = new int[MAX_WORKERS];
		}
		int slot = me.getThreadSlot();
		me.wsRand = new Random();
		me.ws_id = numWorkers;
		workers[numWorkers++] = slot;
		if(RVMThread.wsRetBarrier) {
			me.wsCheckShadowStack_retbarrier(me);
		}
		if(pinLog){
			VM.sysWriteln("[PIN_INFO] W-",me.ws_id, " is on cpuid-",sysCall.sysGetCPU());
		}
	}

	// can be used to verify the result
	public static long wsTotalPush() {
		long totalPushes= 0;
		for(int i=0; i < numWorkers; i++){
			totalPushes += RVMThread.threadBySlot[workers[i]].pushes;
		}
		return totalPushes;
	}

	public static void terminate() {
		if(pinLog){
			VM.sysWriteln("[PIN_INFO] W-",RVMThread.getCurrentThread().ws_id, " is on cpuid-",sysCall.sysGetCPU());
		}
		terminate = true;
	}

	@UninterruptibleNoWarn
	public static void dumpWSStatistics() {
		int steals = 0;
		int totalFindAttempts = 0;
		long totalPushes = 0;
		long successStealCPUCycles = 0;
		long failedStealCPUCycles = 0;
		long totalThiefInstalledBarriers = 0;
		long totalPreInstalledBarriers = 0;
		long barrierCPUCycles = 0;
		
		long tasksEQ2 = 0;	// tasks <= 2
		long tasksLE4 = 0;	// tasks <= 4
		long tasksGT4 = 0;	// tasks <= 8

		for(int i=0; i < numWorkers; i++){
			steals += RVMThread.threadBySlot[workers[i]].totalSteals;
			RVMThread.threadBySlot[workers[i]].totalSteals = 0;
			totalFindAttempts += RVMThread.threadBySlot[workers[i]].findAttempts;
			RVMThread.threadBySlot[workers[i]].findAttempts = 0;
			totalPushes += RVMThread.threadBySlot[workers[i]].pushes;
			RVMThread.threadBySlot[workers[i]].pushes = 0;
			successStealCPUCycles += RVMThread.threadBySlot[workers[i]].totalSuccessStealCPUCycles;
			RVMThread.threadBySlot[workers[i]].totalSuccessStealCPUCycles = 0;
			failedStealCPUCycles += RVMThread.threadBySlot[workers[i]].totalFailedStealCPUCycles;
			RVMThread.threadBySlot[workers[i]].totalFailedStealCPUCycles = 0;
			barrierCPUCycles += RVMThread.threadBySlot[workers[i]].totalBarrierCPUCycles;
			RVMThread.threadBySlot[workers[i]].totalBarrierCPUCycles = 0;
			totalThiefInstalledBarriers += RVMThread.threadBySlot[workers[i]].thiefInstalledBarriers;
			RVMThread.threadBySlot[workers[i]].thiefInstalledBarriers = 0;
			totalPreInstalledBarriers += RVMThread.threadBySlot[workers[i]].preInstalledBarriers;
			RVMThread.threadBySlot[workers[i]].preInstalledBarriers = 0;
			if(RVMThread.createContinuationDistribution) {
				tasksEQ2 += RVMThread.threadBySlot[workers[i]].tasksEQ2;
				RVMThread.threadBySlot[workers[i]].tasksEQ2 = 0;
				tasksLE4 += RVMThread.threadBySlot[workers[i]].tasksLE4;
				RVMThread.threadBySlot[workers[i]].tasksLE4 = 0;
				tasksGT4 += RVMThread.threadBySlot[workers[i]].tasksGT4;
				RVMThread.threadBySlot[workers[i]].tasksGT4 = 0;
			}
			// trace pinning info
			if(pinLog) RVMThread.threadBySlot[workers[i]].trace_cpuid = true;
		}

		// summarize MMTk statistics
		wsSuccessSteals.wsInc(steals);
		wsTasksPushed.wsInc(totalPushes);
		final int failedSteals = totalFindAttempts - steals;
		wsFailedSteals.wsInc(failedSteals);
		wsSuccessStealCycles.wsInc(successStealCPUCycles);
		wsFailedStealCycles.wsInc(failedStealCPUCycles);
		wsBarrierCycles.wsInc(barrierCPUCycles);
		wsThiefInstalledRBarriers.wsInc(totalThiefInstalledBarriers);
		wsPreInstalledRBarriers.wsInc(totalPreInstalledBarriers);
		if(RVMThread.createContinuationDistribution) {
			wsTasksEQ2.wsInc(tasksEQ2);
			wsTasksLE4.wsInc(tasksLE4);
			wsTasksGT4.wsInc(tasksGT4);
		}
		
		// print logs for this iteration

		VM.sysWrite("Statistics: ");
		VM.sysWrite("Total Pushes = ",totalPushes);
		VM.sysWrite(" Total SuccessfulSteals = ",steals);
		VM.sysWrite(" Total SuccessStealCPUCycles = ",successStealCPUCycles);
		VM.sysWrite(" Total FailedSteals = ",failedSteals);
		VM.sysWrite(" Total FailedStealCPUCycles = ",failedStealCPUCycles);
		VM.sysWrite(" Total BarrierCPUCycles = ",barrierCPUCycles);
		VM.sysWrite(" Total ThiefInstalledBarriers = ",totalThiefInstalledBarriers);
		VM.sysWrite(" Total PreInstalledBarriers = ",totalPreInstalledBarriers);

		VM.sysWriteln();
	}

	public static void harnessBegin() {
		if(!RVMThread.autogenWSThread) return;
		for(int i=0; i < numWorkers; i++){
			RVMThread.threadBySlot[workers[i]].totalSteals = 0;
			RVMThread.threadBySlot[workers[i]].findAttempts = 0;
			RVMThread.threadBySlot[workers[i]].pushes = 0;
			RVMThread.threadBySlot[workers[i]].totalSuccessStealCPUCycles = 0;
			RVMThread.threadBySlot[workers[i]].totalFailedStealCPUCycles = 0;
			RVMThread.threadBySlot[workers[i]].totalBarrierCPUCycles = 0;
			RVMThread.threadBySlot[workers[i]].thiefInstalledBarriers = 0;
			RVMThread.threadBySlot[workers[i]].preInstalledBarriers = 0;
			if(RVMThread.createContinuationDistribution) {
				RVMThread.threadBySlot[workers[i]].tasksEQ2 = 0;
				RVMThread.threadBySlot[workers[i]].tasksLE4 = 0;
				RVMThread.threadBySlot[workers[i]].tasksGT4 = 0;
			}
		}
	}

	@UninterruptibleNoWarn
	public static void harnessEnd() {
		if(!RVMThread.autogenWSThread) return;
		int steals = 0;
		int totalFindAttempts = 0;
		long totalPushes = 0;
		long successStealCPUCycles = 0;
		long failedStealCPUCycles = 0;
		long barrierCPUCycles = 0;
		long totalThiefInstalledBarriers = 0;
		long totalPreInstalledBarriers = 0;
		long tasksEQ2 = 0;	// tasks <= 2
		long tasksLE4 = 0;	// tasks <= 4
		long tasksGT4 = 0;	// tasks <= 8

		for(int i=0; i < numWorkers; i++){
			steals += RVMThread.threadBySlot[workers[i]].totalSteals;
			RVMThread.threadBySlot[workers[i]].totalSteals = 0;
			totalFindAttempts += RVMThread.threadBySlot[workers[i]].findAttempts;
			RVMThread.threadBySlot[workers[i]].findAttempts = 0;
			totalPushes += RVMThread.threadBySlot[workers[i]].pushes;
			RVMThread.threadBySlot[workers[i]].pushes = 0;
			successStealCPUCycles += RVMThread.threadBySlot[workers[i]].totalSuccessStealCPUCycles;
			RVMThread.threadBySlot[workers[i]].totalSuccessStealCPUCycles = 0;
			failedStealCPUCycles += RVMThread.threadBySlot[workers[i]].totalFailedStealCPUCycles;
			RVMThread.threadBySlot[workers[i]].totalFailedStealCPUCycles = 0;
			barrierCPUCycles += RVMThread.threadBySlot[workers[i]].totalBarrierCPUCycles;
			RVMThread.threadBySlot[workers[i]].totalBarrierCPUCycles = 0;
			totalThiefInstalledBarriers += RVMThread.threadBySlot[workers[i]].thiefInstalledBarriers;
			RVMThread.threadBySlot[workers[i]].thiefInstalledBarriers = 0;
			totalPreInstalledBarriers += RVMThread.threadBySlot[workers[i]].preInstalledBarriers;
			RVMThread.threadBySlot[workers[i]].preInstalledBarriers = 0;
			if(RVMThread.createContinuationDistribution) {
				tasksEQ2 += RVMThread.threadBySlot[workers[i]].tasksEQ2;
				RVMThread.threadBySlot[workers[i]].tasksEQ2 = 0;
				tasksLE4 += RVMThread.threadBySlot[workers[i]].tasksLE4;
				RVMThread.threadBySlot[workers[i]].tasksLE4 = 0;
				tasksGT4 += RVMThread.threadBySlot[workers[i]].tasksGT4;
				RVMThread.threadBySlot[workers[i]].tasksGT4 = 0;
			}
		}

		// summarize MMTk statistics
		wsSuccessSteals.wsInc(steals);
		wsTasksPushed.wsInc(totalPushes);
		final int failedSteals = totalFindAttempts - steals;
		wsFailedSteals.wsInc(failedSteals);
		wsSuccessStealCycles.wsInc(successStealCPUCycles);
		wsFailedStealCycles.wsInc(failedStealCPUCycles);
		wsThreads.wsInc(numWorkers);
		wsBarrierCycles.wsInc(barrierCPUCycles);
		wsThiefInstalledRBarriers.wsInc(totalThiefInstalledBarriers);
		wsPreInstalledRBarriers.wsInc(totalPreInstalledBarriers);
		if(RVMThread.createContinuationDistribution) {
			wsTasksEQ2.wsInc(tasksEQ2);
			wsTasksLE4.wsInc(tasksLE4);
			wsTasksGT4.wsInc(tasksGT4);
		}
	}

	@Interruptible
	public static void workerMain() {
		register();
		RVMThread.getCurrentThread().wsFlag = false;
		if(RVMThread.wsRetBarrier) {
			searchForWork_retbarrier();
		}
		else {
			searchForWork();
		}
	}

	@Inline
	public static void setFlag() {
		final RVMThread me = RVMThread.getCurrentThread();
		me.pushes++;
		me.wsFlag = true;
	}

	@NoInline
	@Unpreemptible
	public static void join() {
		// Find our caller
		Address fp = Magic.getCallerFramePointer(Magic.getFramePointer());
		Address ip = Magic.getReturnAddress(Magic.getFramePointer());
		RVMThread.getCurrentThread().wsJoinInternal(fp, ip);
	}

	@Inline
	@Unpreemptible
	public static void completeJoin() {
		RVMThread.getCurrentThread().wsCompleteJoinInternal();
	}

	@Inline
	@Unpreemptible
	public static void completeFinishFirst() {
		RVMThread.getCurrentThread().wsCompleteFinishInternal();
	}


	@NoInline
	@Unpreemptible
	public static void finish() {
		// Find our caller
		Address fp = Magic.getCallerFramePointer(Magic.getFramePointer());
		Address ip = Magic.getReturnAddress(Magic.getFramePointer());

		RVMThread t = RVMThread.getCurrentThread();
		FinishInfo finish = t.wsFinishHead;

		if (finish != null) {
			if (VM.VerifyAssertions) VM._assert(!finish.done);
			int compiledMethodId = Magic.getCompiledMethodID(fp);
			CompiledMethod compiledMethod = CompiledMethods.getCompiledMethod(compiledMethodId);
			Offset fpOffset = t.stackTop().diff(fp);
			Offset ipOffset = compiledMethod.getInstructionOffset(ip);
			Offset cbOffset = compiledMethod.findCatchBlockForInstruction(ipOffset, RVMType.WSFinishType, true);

			if (finish.matches(fpOffset, cbOffset)) {
				ObjectModel.genericLock_internal(finish);
				finish.waiting = true;
				boolean done = finish.done = (finish.count == 0);
				if (done) {
					ObjectModel.genericUnlock_internal(finish);
					t.wsFinishHead = finish.prev();
					if (finish.data != null) {
						t.wsFinish.data = finish;
						RuntimeEntrypoints.athrow(t.wsFinish);
					}
				} else {
					finish.steal = t.wsStealHead;
					cbOffset = compiledMethod.findCatchBlockForInstruction(ipOffset, RVMType.WSFinishFirstType, true);
					if (cbOffset.sGE(Offset.zero())) {
						// We want to return data from outside asyncs to a finish.
						t.wsInJoin = true;
						t.wsFinishFirst.finish = finish;
						RuntimeEntrypoints.athrow(t.wsFinishFirst);
					}
					t.wsCompleteFinishInternal();
					// Not reached
				}
			}
		}
	}

	@Uninterruptible
	protected static void incFinish(FinishInfo finish) {
		int old; 
		do {
			old = Magic.prepareInt(finish, Entrypoints.wsFinishCountField.getOffset());
		} while (!Magic.attemptInt(finish, Entrypoints.wsFinishCountField.getOffset(), old, old+1));
	}

	@Unpreemptible
	protected static boolean decFinish(FinishInfo finish) {
		int old; 
		do {
			old = Magic.prepareInt(finish, Entrypoints.wsFinishCountField.getOffset());
		} while (!Magic.attemptInt(finish, Entrypoints.wsFinishCountField.getOffset(), old, old-1));
		if (old == 1) {
			ObjectModel.genericLock_internal(finish);
			if (finish.waiting && !finish.done) {
				finish.done = true;
				ObjectModel.genericUnlock_internal(finish);
				return true;
			}
			ObjectModel.genericUnlock_internal(finish);
		}
		return false;
	}



	/*
	 * Never sleep while searching a victim.
	 * Thieves always keep spinning
	 */
	@UnpreemptibleNoWarn
	@NoInline
	protected static void searchForWork() {
		final RVMThread me = RVMThread.getCurrentThread();
		// trace pin info
		if(me.trace_cpuid && pinLog) {
			me.trace_cpuid = false;
			VM.sysWriteln("[PIN_INFO] W-",me.ws_id, " is on cpuid-",sysCall.sysGetCPU());
		}
		Address fp = Magic.getFramePointer();
		Address callerFp = Magic.getCallerFramePointer(fp);
		Magic.setReturnAddress(callerFp, Address.zero());
		Magic.setCallerFramePointer(callerFp, StackframeLayoutConstants.STACKFRAME_SENTINEL_FP);
		Random rand = me.wsRand;
		while (!terminate) {
			int n = rand.nextInt(numWorkers);
			RVMThread victim = RVMThread.threadBySlot[workers[n]];
			if (!terminate && victim != null && victim.wsFlag && !victim.workstealingInProgress) {
				// thread possibly has work
				RVMThread.wsSteal(victim);
			}
		}
		me.terminate();
	}

	@UnpreemptibleNoWarn
	@NoInline
	protected static void searchForWork_retbarrier() {
		final RVMThread me = RVMThread.getCurrentThread();
		// trace pin info
		if(me.trace_cpuid && pinLog) {
			me.trace_cpuid = false;
			VM.sysWriteln("[PIN_INFO] W-",me.ws_id, " is on cpuid-",sysCall.sysGetCPU());
		}
		if(RVMThread.wsDebugTrace) {
			VM.sysWriteln(me.getId(),": Searching for Work..");
		}
		Address fp = Magic.getFramePointer();
		Address callerFp = Magic.getCallerFramePointer(fp);
		Magic.setReturnAddress(callerFp, Address.zero());
		Magic.setCallerFramePointer(callerFp, StackframeLayoutConstants.STACKFRAME_SENTINEL_FP);
		Random rand = me.wsRand;
		while (!terminate) {
			int n = rand.nextInt(numWorkers);
			RVMThread victim = RVMThread.threadBySlot[workers[n]];
			if (!terminate && victim != null && victim.wsFlag && !victim.workstealingInProgress) {
				// thread possibly has work
				RVMThread.wsSteal_retbarrier(victim);
			}
		}
		me.terminate();
	}

	@Uninterruptible
	protected static abstract class Info {

		protected Offset cbOffset;
		/**
		 * fp offsets are from the base of the stack, so larger offsets mean more recently executed methods.
		 */
		protected Offset fpOffset;

		protected abstract Info prev();

		protected void set(Offset fpOffset, Offset cbOffset) {
			this.fpOffset = fpOffset;
			this.cbOffset = cbOffset;
		}

		protected boolean newerThan(Offset fpOffset) {
			return this.fpOffset.sGT(fpOffset);
		}

		protected boolean newerThan(Info other) {
			return this.fpOffset.sGT(other.fpOffset);
		}

		protected boolean olderThan(Offset fpOffset) {
			return this.fpOffset.sLT(fpOffset);
		}

		protected boolean olderThan(Info other) {
			return this.fpOffset.sLT(other.fpOffset);
		}

		protected boolean matches(Offset fpOffset) {
			return fpOffset.EQ(this.fpOffset);
		}

		protected boolean matches(Offset fpOffset, Offset cbOffset) {
			return fpOffset.EQ(this.fpOffset) && cbOffset.EQ(this.cbOffset);
		}

		protected Info find(Offset fpOffset, Offset cbOffset) {
			Info current = this;
			while (current != null && current.newerThan(fpOffset)) {
				current = current.prev();
			}
			while (current != null && current.matches(fpOffset)) {
				if (current.matches(fpOffset, cbOffset)) {
					return current;
				}
				current = current.prev();
			}
			return null;
		}

		protected void dump() {
			Info current = this;
			while (current != null) {
				VM.sysWriteln(fpOffset, " at ", cbOffset);
				current = current.prev();
			}
		}
	}

	@Uninterruptible
	public static class FinishInfo extends Info implements Iterable<WS.FinishData> {
		private FinishInfo prev;
		private volatile boolean waiting;
		private volatile int count;
		private volatile FinishData data;
		private volatile boolean done;
		protected FinishInfo prev() { return prev; }
		protected void setPrev(FinishInfo prev) { this.prev = prev; }

		protected FinishInfo find(Offset fpOffset, Offset cbOffset) {
			return (FinishInfo)super.find(fpOffset, cbOffset);
		}

		@Interruptible
		public Iterator<FinishData> iterator() {
			return new FinishIterator(data);
		}

		private static class FinishIterator implements Iterator<FinishData> {
			private FinishData current;

			FinishIterator(FinishData head) {
				current = head;
			}

			public boolean hasNext() {
				return current != null;
			}

			public FinishData next() {
				FinishData result = current;
				current = current.next;
				return result;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		}

		@Interruptible
		public void addData(int key, Object value) {
			FinishData newHead = new FinishData(key, value);
			FinishData oldHead;
			do {
				oldHead = data;
				newHead.next = oldHead;
			} while (!Synchronization.tryCompareAndSwap(this, Entrypoints.wsFinishDataField.getOffset(), oldHead, newHead));
		}
		public StealInfo steal;
	}

	public final static class FinishData {
		public FinishData(int key, Object value) {
			this.key = key;
			this.value = value;
		}
		public final int key;
		public final Object value;
		protected FinishData next;
	}

	@Uninterruptible
	protected static class StealInfo extends Info {
		private StealInfo prev;
		protected FinishInfo finish;
		protected Address joinInstInstalledFP;

		protected void set(Offset fpOffset, Offset cbOffset, Address joinInstInstalledFP) {
			this.joinInstInstalledFP = joinInstInstalledFP;
			super.set(fpOffset, cbOffset);
		}
		protected StealInfo prev() { return prev; }
		protected void setPrev(StealInfo prev) { this.prev = prev; }
		protected StealInfo find(Offset fpOffset, Offset cbOffset) {
			return (StealInfo)super.find(fpOffset, cbOffset);
		}
	}

	public static class Finish extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public Iterable<WS.FinishData> data;
	}
	public static class Join extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public FinishInfo finish;
	}
	public static class FinishFirst extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public FinishInfo finish;
	}
	public static class Continuation extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	protected static void installJoinInstructions(Address fp, Address prevFp) {
		Address ip = Magic.getReturnAddress(prevFp);
		CompiledMethod cm = CompiledMethods.getCompiledMethod(Magic.getCompiledMethodID(fp));
		if (cm.hasJoinInstructions()) {
			if (!cm.inJoinInstructions(ip)) { 
				Magic.setReturnAddress(prevFp, ip.plus(cm.joinDelta()));
			}
		}
	}

	protected static void installJoinInstructions(Address fp, Address prevFp, RVMThread victim) {
		final Address ip = prevFp.EQ(victim.getHijackedReturnCalleeFp()) ? victim.getHijackedReturnAddress() : Magic.getReturnAddress(prevFp); 
		CompiledMethod cm = CompiledMethods.getCompiledMethod(Magic.getCompiledMethodID(fp));
		if (cm.hasJoinInstructions()) {
			if (!cm.inJoinInstructions(ip)) {
				//return barrier
				if(prevFp.EQ(victim.getHijackedReturnCalleeFp())) {
					victim.setHijackedReturnAddress(ip.plus(cm.joinDelta()));
				}
				else {
					Magic.setReturnAddress(prevFp, ip.plus(cm.joinDelta()));
				}
			}
		}
	}
}

