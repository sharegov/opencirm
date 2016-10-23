/*******************************************************************************
 * Copyright 2014 Miami-Dade County
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.sharegov.cirm.rdb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class Sql
{
	private static final String AND = ") \nAND (";
	private static final String AND_ARRAY = " \nAND ";
	private static final String OR = ") \nOR (";
	private static final String OR_ARRAY = " \nOR ";
	private static final Set<String> EMPTY_TABLE = new HashSet<String>(){	private static final long serialVersionUID = 75492893884463041L;{this.add("");}};
	
	public static Sql SELECT()
	{
		Sql SELECT = new Select();
		return SELECT;
	}

	public static Sql SELECT_DISTINCT()
	{
		Sql SELECT = new Select();
		SELECT.distinct = true;
		return SELECT;
	}

	public static Sql INSERT_INTO(String table)
	{
		Sql INSERT = new Insert();
		INSERT.tables.push(Collections.singletonMap(table, table));
		return INSERT;
	}

	public static Sql DELETE_FROM(String table)
	{
		Sql DELETE = new Delete();
		DELETE.tables.push(Collections.singletonMap(table, table));
		return DELETE;
	}

	public static Sql UPDATE(String table)
	{
		Sql UPDATE = new Update();
		UPDATE.tables.push(Collections.singletonMap(table, table));
		return UPDATE;
	}
	
	static class Select extends Sql
	{
		public Sql COLUMN(String column)
		{
			if (aliases != columns)
				aliases = columns;
			aliasable = column;
			columns.push(Collections.singletonMap(column, column));
			return this;
		}
		
		public Sql CLEAR_COLUMNS()
		{
			columns.clear();
			return this;
		}
		
		public Sql AS(String alias)
		{
			if (!aliases.isEmpty())
			{
				aliases.pop();
				aliases.push(Collections.singletonMap(aliasable, alias));
				if (aliases == columns && !hasColumnAliases)
					hasColumnAliases = true;
				if (aliases == tables && !hasTableAliases)
					hasTableAliases = true;
			}
			return this;
		}

		public Sql FROM(String table)
		{
			if (aliases != tables)
				aliases = tables;
			aliasable = table;
			tables.push(Collections.singletonMap(table, table));
			return this;
		}

		public Sql JOIN(String join)
		{
			if (join != null)
			{
				this.join.add(join);
				on = this.join;
			}
			return this;
		}

		public Sql INNER_JOIN(String join)
		{
			if (join != null)
			{
				this.innerJoin.add(join);
				on = this.innerJoin;
			}
			return this;
		}

		public Sql LEFT_OUTER_JOIN(String join)
		{
			if (join != null)
			{
				this.leftOuterJoin.add(join);
				on = this.leftOuterJoin;
			}
			return this;
		}

		public Sql RIGHT_OUTER_JOIN(String join)
		{
			if (join != null)
			{
				this.rightOuterJoin.add(join);
				on = this.rightOuterJoin;
			}
			return this;
		}

		public Sql OUTER_JOIN(String join)
		{
			if (join != null)
			{
				this.outerJoin.add(join);
				on = this.outerJoin;
			}
			return this;
		}

		public Sql ON(String columnLeft, String columnRight)
		{
			if (on != null && !on.isEmpty())
			{
				String s = on.get(on.size() - 1);
				on.set(on.size() - 1, s.concat(" ON ").concat(columnLeft).concat(" = ").concat(columnRight));
			}
			return this;
		}

		public Sql ORDER_BY(String column)
		{
			if (column != null)
				this.orderBy.add(column);
			else
				this.orderBy.clear();
			return this;
		}
		
		public Sql ORDER_DIRECTION(String direction)
		{
			if(direction != null)
				this.orderDirection = direction;
			else
				this.orderDirection = null;
			return this;
		}

		public Sql PAGINATION(String column, String value)
		{
			if (column != null)
				this.pagination.put(column, value);
			return this;
		}
		
		public Sql CLEAR_PAGINATION()
		{
			this.pagination.clear();
			return this;
		}

		public Sql WHERE(String where)
		{
			super.WHERE(where);
			this.in = this.where; 
			return this;
		}
		
		private Sql IN(boolean notIn, String... values)
		{
			if (in != null && !in.isEmpty())
			{
				StringBuilder s = new StringBuilder(in.get(in.size() - 1));
				if(notIn)
					s.append(" NOT ");
				s.append(" IN ( ");
				for(int i = 0; i < values.length; i++)
				{
					s.append(values[i]);
					if(!(i == values.length-1))
						s.append(",");
				}
				s.append(" ) ");
				in.set(in.size() - 1, s.toString());
			}
			return this;
		}
		
		private Sql IN(boolean notIn, Sql subquery)
		{
			if (subquery instanceof Select && in != null && !in.isEmpty())
			{
				StringBuilder s = new StringBuilder(in.get(in.size() - 1));
				if(notIn)
					s.append(" NOT ");
				s.append(" IN ( ")
				.append(subquery.SQL())
				.append(" ) ");
				in.set(in.size() - 1, s.toString());
			}
			return this;
		}
		
		public Sql IN(String... values)
		{
			return IN(false, values);
		}
		
		public Sql IN(Sql subquery)
		{
			return IN(false, subquery);
		}
		
		public Sql NOT_IN(String... values)
		{
			return IN(true, values);
		}
		
		public Sql NOT_IN(Sql subquery)
		{
			return IN(true, subquery);
		}
		
		public String SQL()
		{
			StringBuilder builder = new StringBuilder();
			if (distinct)
				sqlClause(builder, "SELECT DISTINCT", columns, "", "", ", ", null, hasColumnAliases);
			else
				sqlClause(builder, "SELECT", columns, "", "", ", ", null, hasColumnAliases);
			sqlClause(builder, "FROM", tables, "", "", ", ", null, hasTableAliases);
			sqlClause(builder, "JOIN", join, "", "", "\nJOIN ", null, false);
			sqlClause(builder, "INNER JOIN", innerJoin, "", "", "\nINNER JOIN ", null, false);
			sqlClause(builder, "OUTER JOIN", outerJoin, "", "", "\nOUTER JOIN ", null, false);
			sqlClause(builder, "LEFT OUTER JOIN", leftOuterJoin, "", "", "\nLEFT OUTER JOIN ", null, false);
			sqlClause(builder, "RIGHT OUTER JOIN", rightOuterJoin, "", "", "\nRIGHT OUTER JOIN ", null, false);
			sqlClause(builder, "WHERE", where, "(", ")", " AND ", null, false);
			sqlClause(builder, "GROUP BY", groupBy, "", "", ", ", null, false);
			sqlClause(builder, "HAVING", having, "(", ")", " AND ", null, false);
			sqlClause(builder, "ORDER BY", orderBy, "", "", ", ", null, false);
			
			if(!pagination.isEmpty()) 
			{
			    if (dbhook != null)
			        return dbhook.paginate(builder.toString(), 
			                               Long.parseLong(pagination.get("minValue").toString()), 
			                               Long.parseLong(pagination.get("maxValue").toString()));
			    else
			        return SELECT()
    					.COLUMN("*")
    					.FROM("(" +	
    						SELECT()
    						.COLUMN("a.*")
    						.COLUMN("rownum rnum")
    						.FROM("("+builder.toString()+") a")
    						.WHERE("rownum")
    						.LESS_THAN_OR_EQUAL(pagination.get("maxValue")).SQL()
    					+")")
    					.WHERE("rnum")
    					.GREATER_THAN_OR_EQUAL(pagination.get("minValue")).SQL();
			}
			else
			    return builder.toString();
		}
	}

	static class Insert extends Sql
	{
		private boolean mergeInsert = false;
		
		public boolean isMergeInsert()
		{
			return mergeInsert;
		}

		public void setMergeInsert(boolean mergeInsert)
		{
			this.mergeInsert = mergeInsert;
		}
		
		public Sql VALUES(String columns, String values)
		{
			if(columns != null)
			{
			this.columns.push(Collections.singletonMap(columns,columns));
			this.values.add(values);
			}
			return this;
		}
		
		public Sql WHERE(String where)
		{
			return this;
		}

		public String SQL()
		{
			StringBuilder builder = new StringBuilder();
			sqlClause(builder, (!mergeInsert)?"INSERT INTO":"INSERT", (!mergeInsert)?tables:EMPTY_TABLE, "", "", "", null, false);
			sqlClause(builder, "", columns, "(", ")", ", ", null, false);
			sqlClause(builder, "VALUES", values, "(", ")", ", ", null, false);
			return builder.toString();
		}
	}

	static class Update extends Sql
	{
		private boolean mergeUpdate = false;
		
		public boolean isMergeUpdate()
		{
			return mergeUpdate;
		}

		public void setMergeUpdate(boolean mergeUpdate)
		{
			this.mergeUpdate = mergeUpdate;
		}

		public Sql SET(String columns, String values)
		{
			if(columns != null)
			{
				this.sets.add(columns + " = " + values);
			}
			return this;
		}
		
		public String SQL()
		{
			StringBuilder builder = new StringBuilder();
			sqlClause(builder, "UPDATE", (!mergeUpdate)?tables:EMPTY_TABLE, "", "", "", null, false);
			sqlClause(builder, "SET", sets, "", "", ", ", null, false);
			sqlClause(builder, "WHERE", where, "(", ")", " AND ", null, false);
			return builder.toString();
		}
	}

	static class Delete extends Sql
	{
		public Sql WHERE(String where)
		{
			super.WHERE(where);
			this.in = this.where; 
			return this;
		}
		
		private Sql IN(boolean notIn, String... values)
		{
			if (in != null && !in.isEmpty())
			{
				StringBuilder s = new StringBuilder(in.get(in.size() - 1));
				if(notIn)
					s.append(" NOT ");
				s.append(" IN ( ");
				for(int i = 0; i < values.length; i++)
				{
					s.append(values[i]);
					if(!(i == values.length-1))
						s.append(",");
				}
				s.append(" ) ");
				in.set(in.size() - 1, s.toString());
			}
			return this;
		}
		
		private Sql IN(boolean notIn, Sql subquery)
		{
			if (subquery instanceof Select && in != null && !in.isEmpty())
			{
				StringBuilder s = new StringBuilder(in.get(in.size() - 1));
				if(notIn)
					s.append(" NOT ");
				s.append(" IN ( ")
				.append(subquery.SQL())
				.append(" ) ");
				in.set(in.size() - 1, s.toString());
			}
			return this;
		}
		
		public Sql IN(String... values)
		{
			return IN(false, values);
		}
		
		public Sql IN(Sql subquery)
		{
			return IN(false, subquery);
		}
		
		public Sql NOT_IN(String... values)
		{
			return IN(true, values);
		}
		
		public Sql NOT_IN(Sql subquery)
		{
			return IN(true, subquery);
		}
		
		public String SQL()
		{
			StringBuilder builder = new StringBuilder();
			sqlClause(builder, "DELETE FROM", tables, "", "", "", null, false);
			sqlClause(builder, "WHERE", where, "(", ")", " AND ", null, false);
			return builder.toString();
		}
	}
	
	static class Merge extends Sql
	{
		private String table;
		private String alias;
		private Select select;
		private String selectTable;
		private String selectAlias;
		private Update update;
		private Insert insert;
		private Map<String,String> on;
		private String columnLeft;
		private String columnRight;
		
		public Merge()
		{
			
		}
		
		public Merge(String table)
		{
			this.table = table;
		}
		
		public Merge(String table, String alias)
		{
			this(table);
			this.alias = alias;
		}
		
		public Sql ON(String columnLeft, String columnRight)
		{
			if(this.on == null)
				this.on = new LinkedHashMap<String, String>();
			this.on.put(columnLeft, columnRight);
			this.columnLeft = columnLeft;
			this.columnRight = columnRight;
			return this;
		}

		public Sql USING(Sql select, String alias)
		{
			if(select instanceof Select)
			{	this.select = (Select) select;
				this.selectAlias = alias;
			}
			return this;
		}

		public Sql USING(String table, String alias)
		{
			this.selectTable = table;
			this.selectAlias = alias;
			return null;
		}

		public Sql WHEN_MATCHED_THEN(Sql update)
		{
			if(update instanceof Update)
			{
				this.update = (Update)update;
				this.update.setMergeUpdate(true);
			}
			return this;
		}

		public Sql WHEN_NOT_MATCHED_THEN(Sql insert)
		{
			if(insert instanceof Insert)
			{
				this.insert = (Insert)insert;
				this.insert.setMergeInsert(true);
			}
			return this;
		}

		public String SQL()
		{
			StringBuilder builder = new StringBuilder();
			builder.append("MERGE INTO \n");
			builder.append(table);
			if(alias != null)
				builder.append(" ").append(alias).append(" ");
			builder.append(" USING ");
			if(select != null)
				builder.append("\n(").append(select.SQL()).append(")\n");
			else if(selectTable != null)
				builder.append(selectTable);
			if(selectAlias != null)
				builder.append(" ").append(selectAlias).append(" ");
			builder.append(" ON ( ");
			for(Map.Entry<String, String> entry: on.entrySet() )
					builder.append(entry.getKey()).append(" = ").append(entry.getValue()).append(" AND ");
			builder.delete(builder.lastIndexOf(" AND "), builder.length());
			builder.append(" ) ");
			builder.append("\n WHEN MATCHED THEN \n");
			if(update != null)
				builder.append(update.SQL());
			builder.append("\n WHEN NOT MATCHED THEN \n");
			if(insert != null)
				builder.append(insert.SQL());
			return builder.toString();
		}
	}

	public Sql COLUMN(String column)
	{
		return this;
	}
	
	public Sql CLEAR_COLUMNS()
	{
		return this;
	}
	
	public Sql FROM(String table)
	{
		return this;
	}

	public Sql AS(String alias)
	{
		return this;
	}

	public Sql JOIN(String table)
	{
		return this;
	}

	public Sql INNER_JOIN(String join)
	{
		return this;
	}

	public Sql LEFT_OUTER_JOIN(String join)
	{
		return this;
	}

	public Sql RIGHT_OUTER_JOIN(String join)
	{
		return this;
	}

	public Sql OUTER_JOIN(String join)
	{
		return this;
	}

	public Sql ON(String columnLeft, String columnRight)
	{
		return this;
	}
	
	public Sql ORDER_BY(String column)
	{
		return this;
	}
	
	public Sql ORDER_DIRECTION(String direction)
	{
		return this;
	}
	
	public Sql PAGINATION(String column, String value)
	{
		return this;
	}
	
	public Sql CLEAR_PAGINATION()
	{
		return this;
	}

	public Sql WHERE(String where)
	{
		this.where.add(where);
		conjunctionList = this.where;
		operationsList = this.where;
		return this;
	}
	

	public Sql EQUALS(String right)
	{
		if (operationsList != null && !operationsList.isEmpty())
		{
			String s = operationsList.get(operationsList.size() - 1);
			operationsList.set(operationsList.size() - 1, s.concat(" = ").concat(right));
		}
		return this;
	}

	public Sql NOT_LIKE(String right)
	{
		if (operationsList != null && !operationsList.isEmpty())
		{
			String s = operationsList.get(operationsList.size() - 1);
			operationsList.set(operationsList.size() - 1, s.concat(" NOT LIKE ").concat(right));
		}
		return this;
	}

	public Sql LIKE(String right)
	{
		if (operationsList != null && !operationsList.isEmpty())
		{
			String s = operationsList.get(operationsList.size() - 1);
			operationsList.set(operationsList.size() - 1, s.concat(" LIKE ").concat(right));
		}
		return this;
	}

	public Sql LESS_THAN(String right)
	{
		if (operationsList != null && !operationsList.isEmpty())
		{
			String s = operationsList.get(operationsList.size() - 1);
			operationsList.set(operationsList.size() - 1, s.concat(" < ").concat(right));
		}
		return this;
	}

	public Sql GREATER_THAN(String right)
	{
		if (operationsList != null && !operationsList.isEmpty())
		{
			String s = operationsList.get(operationsList.size() - 1);
			operationsList.set(operationsList.size() - 1, s.concat(" > ").concat(right));
		}
		return this;
	}

	public Sql LESS_THAN_OR_EQUAL(String right)
	{
		if (operationsList != null && !operationsList.isEmpty())
		{
			String s = operationsList.get(operationsList.size() - 1);
			operationsList.set(operationsList.size() - 1, s.concat(" <= ").concat(right));
		}
		return this;
	}

	public Sql GREATER_THAN_OR_EQUAL(String right)
	{
		if (operationsList != null && !operationsList.isEmpty())
		{
			String s = operationsList.get(operationsList.size() - 1);
			operationsList.set(operationsList.size() - 1, s.concat(" >= ").concat(right));
		}
		return this;
	}

	public Sql BETWEEN(String lower, String upper)
	{
		if (operationsList != null && !operationsList.isEmpty())
		{
			String s = operationsList.get(operationsList.size() - 1);
			operationsList.set(operationsList.size() - 1, s.concat(" BETWEEN ").concat(lower).concat(" AND ").concat(
					upper));
		}
		return this;
	}

	public Sql AND()
	{
		if (!conjunctionList.isEmpty())
			conjunctionList.add(AND);
		return this;
	}

	public Sql AND_ARRAY()
	{
		if (!conjunctionList.isEmpty())
			conjunctionList.add(AND_ARRAY);
		return this;
	}

	public Sql OR()
	{
		if (!conjunctionList.isEmpty())
			conjunctionList.add(OR);
		return this;
	}
	
	public Sql OR_ARRAY()
	{
		if (!conjunctionList.isEmpty())
			conjunctionList.add(OR_ARRAY);
		return this;
	}

	public Sql IN(String... values)
	{
		return this;
	}
	
	public Sql IN(Sql subquery)
	{
		return this;
	}
	
		
	public Sql NOT_IN(String... values)
	{
		return this;
	}
	
	public Sql NOT_IN(Sql subquery)
	{
		return this;
	}
	
	public Sql VALUES(String column, String values)
	{
		return this;
	}
	
	public Sql SET(String column, String values)
	{
		return this;
	}
	
	public String SQL()
	{
		return "";
	}
	
	public static Sql MERGE_INTO(String table)
	{
		Sql MERGE = new Merge(table);
		return MERGE;
	}
	
	public static Sql MERGE_INTO(String table, String alias)
	{
		Sql MERGE = new Merge(table, alias);
		return MERGE;
	}
	public Sql USING(String table, String alias)
	{
		return this;
	}
	
	public Sql USING(Sql select, String alias)
	{
		return this;
	}
	
	public Sql WHEN_MATCHED_THEN(Sql update)
	{
		return this;
	}
	
	public Sql WHEN_NOT_MATCHED_THEN(Sql insert)
	{
		return this;
	}
	
	public List<String> COLUMNS()
	{
		List<String> result = new ArrayList<String>(columns.size());
		for(Map<String,String> columns : this.columns)
		{
			result.add(columns.keySet().iterator().next());
		}
		return result;
	}

    public Sql DB(DatabaseHook dbhook)
    {
        this.dbhook = dbhook;
        return this;
    }
	
	boolean distinct;
	Stack<Map<String,String>> tables = new Stack<Map<String,String>>();
	Stack<Map<String,String>> columns = new Stack<Map<String,String>>();
	Map<String, String> pagination = new LinkedHashMap<String, String>(); 
	List<String> sets = new ArrayList<String>();
	List<String> join = new ArrayList<String>();
	List<String> innerJoin = new ArrayList<String>();
	List<String> outerJoin = new ArrayList<String>();
	List<String> leftOuterJoin = new ArrayList<String>();
	List<String> rightOuterJoin = new ArrayList<String>();
	List<String> where = new ArrayList<String>();
	List<String> having = new ArrayList<String>();
	List<String> groupBy = new ArrayList<String>();
	List<String> orderBy = new ArrayList<String>();
	String orderDirection = new String();
	List<String> lastList = new ArrayList<String>();
	List<String> values = new ArrayList<String>();
	Stack<Map<String,String>> aliases = columns;
	List<String> conjunctionList = where;
	List<String> on;
	List<String> operationsList;
	List<String> in;
	String aliasable;
	boolean hasColumnAliases;
	boolean hasTableAliases;
    DatabaseHook dbhook = null;
    
	protected void sqlClause(StringBuilder builder, String keyword, Collection<?> parts, String open,
			String close, String conjunction, Map<String, String> aliases, boolean useAlias)
	{
		if (parts!= null && !parts.isEmpty())
		{
			if (builder.length() > 0)
				builder.append("\n");
			builder.append(keyword);
			builder.append(" ");
			builder.append(open);
			String last = "________";
			String alias = null;
			int i = 0;
			for (Object part : parts)
			{
				String p = null;
				if(part instanceof String)
					p = (String) part;
				else if(part instanceof Map<?, ?>)
				{
					Map.Entry<?, ?> entry = ((Map<?,?>)part).entrySet().iterator().next();
					p = (String)entry.getKey();
					alias = (String)entry.getValue();
				}
				if (i > 0 && !p.equals(AND) && !p.equals(OR) && !last.equals(AND) && !last.equals(OR) 
						&& !p.equals(OR_ARRAY) && !last.equals(OR_ARRAY) && !p.equals(AND_ARRAY) && !last.equals(AND_ARRAY))
				{
					builder.append(conjunction);
				}
				builder.append(p);
	 			if(keyword.equals("ORDER BY") && !orderDirection.trim().isEmpty())
					builder.append(" ").append(orderDirection);
				if (alias != null && useAlias)
					builder.append(" AS ").append(alias);
				last = (String)p;
				i++;
			}
			builder.append(close);
		}
	}
}