package com.exascale.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import com.exascale.managers.HRDBMSWorker;
import com.exascale.managers.ResourceManager;
import com.exascale.managers.ResourceManager.DiskBackedHashSet;
import com.exascale.misc.BufferedLinkedBlockingQueue;
import com.exascale.misc.DataEndMarker;
import com.exascale.tables.Plan;
import com.exascale.threads.ThreadPoolThread;

public final class IntersectOperator implements Operator, Serializable
{
	private MetaData meta;

	private final ArrayList<Operator> children = new ArrayList<Operator>();

	private Operator parent;
	private HashMap<String, String> cols2Types;
	private HashMap<String, Integer> cols2Pos;
	private TreeMap<Integer, String> pos2Col;
	private int node;
	private ArrayList<DiskBackedHashSet> sets = new ArrayList<DiskBackedHashSet>();
	private BufferedLinkedBlockingQueue buffer;
	private int estimate = 16;
	private volatile boolean inited = false;
	private volatile boolean startDone = false;
	private Plan plan;
	
	public void setPlan(Plan plan)
	{
		this.plan = plan;
	}

	public IntersectOperator(MetaData meta)
	{
		this.meta = meta;
	}

	@Override
	public void add(Operator op) throws Exception
	{
		children.add(op);
		op.registerParent(this);
		cols2Types = op.getCols2Types();
		cols2Pos = op.getCols2Pos();
		pos2Col = op.getPos2Col();
	}

	@Override
	public ArrayList<Operator> children()
	{
		return children;
	}

	@Override
	public IntersectOperator clone()
	{
		final IntersectOperator retval = new IntersectOperator(meta);
		retval.node = node;
		retval.estimate = estimate;
		return retval;
	}

	@Override
	public void close() throws Exception
	{
		for (final DiskBackedHashSet set : sets)
		{
			set.getArray().close();
			set.close();
		}
	}

	@Override
	public int getChildPos()
	{
		return 0;
	}

	@Override
	public HashMap<String, Integer> getCols2Pos()
	{
		return cols2Pos;
	}

	@Override
	public HashMap<String, String> getCols2Types()
	{
		return cols2Types;
	}

	@Override
	public MetaData getMeta()
	{
		return meta;
	}

	@Override
	public int getNode()
	{
		return node;
	}

	@Override
	public TreeMap<Integer, String> getPos2Col()
	{
		return pos2Col;
	}

	@Override
	public ArrayList<String> getReferences()
	{
		return null;
	}

	@Override
	public Object next(Operator op2) throws Exception
	{
		Object o;
		o = buffer.take();

		if (o instanceof DataEndMarker)
		{
			o = buffer.peek();
			if (o == null)
			{
				buffer.put(new DataEndMarker());
				return new DataEndMarker();
			}
			else
			{
				buffer.put(new DataEndMarker());
				return o;
			}
		}
		return o;
	}

	@Override
	public void nextAll(Operator op) throws Exception
	{
		for (final Operator o : children)
		{
			o.nextAll(op);
		}
		Object o = next(op);
		while (!(o instanceof DataEndMarker))
		{
			o = next(op);
		}
	}

	@Override
	public Operator parent()
	{
		return parent;
	}

	@Override
	public void registerParent(Operator op) throws Exception
	{
		if (parent == null)
		{
			parent = op;
		}
		else
		{
			throw new Exception("IntersectOperator only supports 1 parent.");
		}
	}

	@Override
	public void removeChild(Operator op)
	{
		children.remove(op);
		op.removeParent(this);
	}

	@Override
	public void removeParent(Operator op)
	{
		parent = null;
	}

	@Override
	public void reset()
	{
		if (!startDone)
		{
			try
			{
				start();
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				System.exit(1);
			}
		}
		else
		{
			inited = false;
			for (final Operator op : children)
			{
				op.reset();
			}

			for (final DiskBackedHashSet set : sets)
			{
				try
				{
					set.getArray().close();
					set.close();
				}
				catch (final Exception e)
				{
					HRDBMSWorker.logger.error("", e);
					System.exit(1);
				}
			}

			sets = new ArrayList<DiskBackedHashSet>();
			buffer.clear();
			if (!inited)
			{
			}
			else
			{
				Exception e = new Exception();
				HRDBMSWorker.logger.error("IntersectOperator is inited more than once!");
				System.exit(1);
			}
			new InitThread().start();
		}
	}

	@Override
	public void setChildPos(int pos)
	{
	}

	public void setEstimate(int estimate)
	{
		this.estimate = estimate;
	}

	@Override
	public void setNode(int node)
	{
		this.node = node;
	}

	@Override
	public void start() throws Exception
	{
		startDone = true;
		for (final Operator op : children)
		{
			op.start();
		}

		buffer = new BufferedLinkedBlockingQueue(ResourceManager.QUEUE_SIZE);
		if (!inited)
		{
		}
		else
		{
			Exception e = new Exception();
			HRDBMSWorker.logger.error("IntersectOperator is inited more than once!", e);
			System.exit(1);
		}
		new InitThread().start();
	}

	@Override
	public String toString()
	{
		return "IntersectOperator";
	}

	private final class InitThread extends ThreadPoolThread
	{
		private final ArrayList<ReadThread> threads = new ArrayList<ReadThread>(children.size());

		@Override
		public void run()
		{
			if (!inited)
			{
				inited = true;
			}
			else
			{
				Exception e = new Exception();
				HRDBMSWorker.logger.error("IntersectOperator is inited more than once!", e);
				System.exit(1);
			}
			if (children.size() == 1)
			{
				int count = 0;
				try
				{
					Object o = children.get(0).next(IntersectOperator.this);
					while (!(o instanceof DataEndMarker))
					{
						while (true)
						{
							try
							{
								buffer.put(o);
								count++;
								break;
							}
							catch (final Exception e)
							{
							}
						}

						o = children.get(0).next(IntersectOperator.this);
					}

					HRDBMSWorker.logger.debug("Intersect operator returned " + count + " rows");

					while (true)
					{
						try
						{
							buffer.put(o);
							break;
						}
						catch (final Exception e)
						{
						}
					}
				}
				catch (final Exception f)
				{
					HRDBMSWorker.logger.error("", f);
					System.exit(1);
				}

				return;
			}
			int i = 0;
			while (i < children.size())
			{
				final ReadThread read = new ReadThread(i);
				threads.add(read);
				sets.add(ResourceManager.newDiskBackedHashSet(true, estimate));
				read.start();
				i++;
			}

			for (final ReadThread read : threads)
			{
				while (true)
				{
					try
					{
						read.join();
						break;
					}
					catch (final InterruptedException e)
					{
					}
				}
			}

			i = 0;
			long minCard = Long.MAX_VALUE;
			int minI = -1;
			for (final ReadThread read : threads)
			{
				final long card = sets.get(i).size();
				if (card < minCard)
				{
					minCard = card;
					minI = i;
				}

				i++;
			}

			int count = 0;
			for (final Object o : sets.get(minI).getArray())
			{
				boolean inAll = true;
				for (final DiskBackedHashSet set : sets)
				{
					if (!set.contains(o))
					{
						inAll = false;
						break;
					}
				}

				if (inAll)
				{
					while (true)
					{
						try
						{
							buffer.put(o);
							count++;
							break;
						}
						catch (final Exception e)
						{
						}
					}
				}
			}

			HRDBMSWorker.logger.debug("Intersect operator returned " + count + " rows");

			while (true)
			{
				try
				{
					buffer.put(new DataEndMarker());
					break;
				}
				catch (final Exception e)
				{
				}
			}
		}
	}

	private final class ReadThread extends ThreadPoolThread
	{
		private final Operator op;
		private final int i;

		public ReadThread(int i)
		{
			this.i = i;
			op = children.get(i);
		}

		@Override
		public void run()
		{
			try
			{
				final DiskBackedHashSet set = sets.get(i);
				Object o = op.next(IntersectOperator.this);
				while (!(o instanceof DataEndMarker))
				{
					set.add((ArrayList<Object>)o);
					o = op.next(IntersectOperator.this);
				}
			}
			catch (final Exception e)
			{
				HRDBMSWorker.logger.error("", e);
				System.exit(1);
			}
		}
	}
}