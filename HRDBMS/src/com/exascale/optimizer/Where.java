package com.exascale.optimizer;

public class Where
{
	private SearchCondition search;
	
	public Where(SearchCondition search)
	{
		this.search = search;
	}
	
	public SearchCondition getSearch()
	{
		return search;
	}
}