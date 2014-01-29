package com.exascale.locking;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import com.exascale.exceptions.LockAbortException;
import com.exascale.filesystem.Block;
import com.exascale.managers.HRDBMSWorker;
import com.exascale.misc.MultiHashMap;

public class LockTable 
{
	protected static int MAX_TIME_SECS;
	protected static ConcurrentHashMap<Block, Integer> locks = new ConcurrentHashMap<Block, Integer>();
	protected static MultiHashMap<Block, Thread> waitList = new MultiHashMap<Block, Thread>();
	public static boolean blockSLocks = false;
	
	public LockTable()
	{
		MAX_TIME_SECS = Integer.parseInt(HRDBMSWorker.getHParms().getProperty("deadlock_timeout_secs"));
	}
	
	//waiting on a lock can't hold a sync on the whole lock table
	public static void sLock(Block b) throws LockAbortException
	{
		while (blockSLocks)
		{
			try
			{
				Thread.sleep(Long.parseLong(HRDBMSWorker.getHParms().getProperty("slock_block_sleep_ms")));
			}
			catch(InterruptedException e)
			{}
		}
		
		try
		{
			long time = System.currentTimeMillis();
			while (true)
			{
				while (hasXLock(b) && !waitingTooLong(time))
				{
					waitList.multiPut(b, Thread.currentThread());
					Thread.currentThread().wait(MAX_TIME_SECS * 1000);
				}

				waitList.multiRemove(b, Thread.currentThread());

				synchronized(locks)
				{
					if (hasXLock(b))
					{
						if (waitingTooLong(time))
						{
							throw new LockAbortException();
						}
						continue;
					}
			
					int val = getLockVal(b);
					locks.put(b, val+1);
					break;
				}
			}
		}
		catch(Exception e)
		{
			HRDBMSWorker.logger.error("Error trying to obtain slock.  Throwing LockAbortException.", e);
			throw new LockAbortException();
		}
	}
	
	public static void xLock(Block b) throws LockAbortException
	{
		try
		{
			long time = System.currentTimeMillis();
			while (true)
			{
				while (hasOtherSLocks(b) && !waitingTooLong(time))
				{
					waitList.multiPut(b, Thread.currentThread());
					Thread.currentThread().wait(MAX_TIME_SECS * 1000);
				}

				waitList.multiRemove(b, Thread.currentThread());
			
				synchronized(locks)
				{
					if (hasOtherSLocks(b))
					{
						if (waitingTooLong(time))
						{
							throw new LockAbortException();
						}
					
						continue;
					}
			
					locks.put(b,  -1);
					break;
				}
			}
		}
		catch(Exception e)
		{
			HRDBMSWorker.logger.error("Exception occurred trying to obtain xlock. LockAbortException will be thrown.", e);
			throw new LockAbortException();
		}
	}
	
	public static void unlock(Block b)
	{
		synchronized(locks)
		{
			int val = getLockVal(b);
			if (val > 1)
			{
				locks.put(b,  val-1);
			}
			else
			{
				locks.remove(b);
				for (Thread thread : waitList.get(b))
				{
					thread.notify();
				}
			}
		}
	}
	
	protected static boolean hasXLock(Block b)
	{
		return getLockVal(b) < 0;
	}
	
	protected static boolean hasOtherSLocks(Block b)
	{
		return getLockVal(b) > 1;
	}
	
	protected static boolean waitingTooLong(long time)
	{
		long now = System.currentTimeMillis();
		return (now - time) > (MAX_TIME_SECS * 1000);
	}
	
	protected static int getLockVal(Block b)
	{
		Integer ival;
		ival = locks.get(b);
		return (ival == null) ? 0 : ival.intValue();
	}
}
