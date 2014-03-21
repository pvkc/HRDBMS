package com.exascale.threads;

import com.exascale.filesystem.Block;
import com.exascale.managers.BufferManager;
import com.exascale.managers.HRDBMSWorker;

public class IOThread extends HRDBMSThread
{
	private final Block[] bs;
	private final long txnum;

	public IOThread(Block b, long txnum)
	{
		this.setWait(false);
		this.description = ("I/O for " + b);
		this.bs = new Block[1];
		bs[0] = b;
		this.txnum = txnum;
	}

	public IOThread(Block[] bs, long txnum)
	{
		this.setWait(false);
		this.description = ("I/O for multiple blocks");
		this.bs = bs;
		this.txnum = txnum;
	}

	@Override
	public void run()
	{
		try
		{
			for (final Block b : bs)
			{
				BufferManager.pin(b, txnum);
			}
		}
		catch (final Exception e)
		{
			HRDBMSWorker.logger.error("Error occurred in an I/O thread.", e);
		}

		this.terminate();
		return;
	}
}
