
/*
 * Authors: Vivek Kumar
 */

package org.jikesrvm.scheduler;

public class ParallelForWS {
	public interface Body {
		void iters(int from, int to);
	}
	
	public static void launch(Body body, int limit) throws Exception {
		_$dcFor(0, limit, 1, WS.wsProcs, body);
	}

	private static void _$dcFor(int _$lower, int _$upper, int _$sliceNum, int procs, Body body) throws Exception {
		if (_$sliceNum >> 2 < procs) {
			int var0 = _$lower + _$upper >> 1;
			int var1 = _$sliceNum << 1;
			try {
				WS.setFlag();
				_$dcFor(_$lower, var0, var1, procs, body);
				WS.join();
			} catch (WS.Continuation c) {}
			_$dcFor(var0, _$upper, var1, procs, body);
		} else {
			body.iters(_$lower, _$upper);
		}
	}

	public static void launch_noThrowException(Body body, int limit) {
		_$dcFor_noThrowException(0, limit, 1, WS.wsProcs, body);
	}

	private static void _$dcFor_noThrowException(int _$lower, int _$upper, int _$sliceNum, int procs, Body body) {
		if (_$sliceNum >> 2 < procs) {
			int var0 = _$lower + _$upper >> 1;
			int var1 = _$sliceNum << 1;
			try {
				WS.setFlag();
				_$dcFor_noThrowException(_$lower, var0, var1, procs, body);
				WS.join();
			} catch (WS.Continuation c) {}
			_$dcFor_noThrowException(var0, _$upper, var1, procs, body);
		} else {
			body.iters(_$lower, _$upper);
		}
	}
}
