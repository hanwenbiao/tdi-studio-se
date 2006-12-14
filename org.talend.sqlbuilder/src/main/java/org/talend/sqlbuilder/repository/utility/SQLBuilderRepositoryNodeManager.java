// ============================================================================
//
// Talend Community Edition
//
// Copyright (C) 2006 Talend - www.talend.com
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
//
// ============================================================================
package org.talend.sqlbuilder.repository.utility;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.talend.commons.exception.PersistenceException;
import org.talend.commons.utils.VersionUtils;
import org.talend.core.CorePlugin;
import org.talend.core.context.Context;
import org.talend.core.context.RepositoryContext;
import org.talend.core.model.metadata.IMetadataConnection;
import org.talend.core.model.metadata.IMetadataTable;
import org.talend.core.model.metadata.builder.ConvertionHelper;
import org.talend.core.model.metadata.builder.connection.ConnectionFactory;
import org.talend.core.model.metadata.builder.connection.DatabaseConnection;
import org.talend.core.model.metadata.builder.connection.MetadataColumn;
import org.talend.core.model.metadata.builder.connection.MetadataTable;
import org.talend.core.model.metadata.builder.connection.QueriesConnection;
import org.talend.core.model.metadata.builder.connection.Query;
import org.talend.core.model.metadata.builder.database.ExtractMetaDataFromDataBase;
import org.talend.core.model.metadata.builder.database.ExtractMetaDataUtils;
import org.talend.core.model.properties.DatabaseConnectionItem;
import org.talend.core.model.properties.ItemState;
import org.talend.core.model.properties.PropertiesFactory;
import org.talend.core.model.properties.Property;
import org.talend.core.model.repository.IRepositoryObject;
import org.talend.core.model.repository.RepositoryObject;
import org.talend.repository.model.ProxyRepositoryFactory;
import org.talend.repository.model.RepositoryNode;
import org.talend.repository.model.RepositoryNode.ENodeType;
import org.talend.repository.model.RepositoryNode.EProperties;
import org.talend.repository.ui.utils.ManagerConnection;
import org.talend.sqlbuilder.SqlBuilderPlugin;
import org.talend.sqlbuilder.dbstructure.RepositoryNodeType;
import org.talend.sqlbuilder.dbstructure.SqlBuilderRepositoryObject;
import org.talend.sqlbuilder.dbstructure.DBTreeProvider.MetadataTableRepositoryObject;
import org.talend.sqlbuilder.util.ConnectionParameters;

/**
 * DOC dev class global comment. Detailled comment <br/>
 * 
 * $Id: talend-code-templates.xml 1 2006-09-29 17:06:40 +0000 (Fri, 29 Sep 2006)
 * nrousseau $
 * 
 */
public class SQLBuilderRepositoryNodeManager {

	// /store all DatabaseConnection's RepositoryNode.
	private static List<RepositoryNode> repositoryNodes = new ArrayList<RepositoryNode>();

	// store all label name of connections's tables and columns
	private static Map<String, String> labelsAndNames = new HashMap<String, String>();

	private static Map<DatabaseConnection, Map<MetadataTable, List<MetadataColumn>>> oldMetaData = 
		new HashMap<DatabaseConnection, Map<MetadataTable, List<MetadataColumn>>>();

	private static boolean isDialogClosed = false;

	private static boolean isReduction = false;
	
	private static boolean isIncrease = false;
	
	private static boolean isFirst = true;
	
	private static String currentNodeLabel = "";
	
//	public static boolean isAction = false;
	/**
	 * Getter for oldMetaData.
	 * 
	 * @return the oldMetaData
	 */
	public static Map<DatabaseConnection, Map<MetadataTable, List<MetadataColumn>>> getOldMetaData() {
		return oldMetaData;
	}

	/**
	 * DOC dev Comment method "isChangeElementColor".
	 * 
	 * @param node
	 * @return
	 */
	public boolean isChangeElementColor(RepositoryNode node) {
		boolean flag = false;
		Object type = node.getProperties(EProperties.CONTENT_TYPE);
		if (type.equals(RepositoryNodeType.DATABASE)) {
			return getItem(node).getConnection().isDivergency();
		}

		if (type.equals(RepositoryNodeType.TABLE)) {
			MetadataTableRepositoryObject object = (MetadataTableRepositoryObject) node
					.getObject();
			MetadataTable table = object.getTable();
			flag = table.getSourceName().equals(table.getLabel());
			flag = flag && table.isDivergency();
		}
		return flag;
	}

	/**
	 * DOC dev Comment method "isDiff".
	 * 
	 * @param node
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public boolean[] isDiff(RepositoryNode node) {
		boolean isDiffDivergency = false;
		boolean isDiffSyschronize = false;
		boolean isDiffGray = false;
		Object type = node.getProperties(EProperties.CONTENT_TYPE);
		if (type.equals(RepositoryNodeType.DATABASE)) {
			DatabaseConnection connection = (DatabaseConnection) getItem(node)
					.getConnection();
			List<MetadataTable> tables = connection.getTables();
			for (MetadataTable table : tables) {
				List<MetadataColumn> columns = table.getColumns();
				for (MetadataColumn column : columns) {
					if (column.isDivergency()) {
						isDiffDivergency = true;
					}
					if (column.isSynchronised()) {
						isDiffSyschronize = true;
					}
					if (column.getLabel() == null
							|| "".equals(column.getLabel())) {
						isDiffGray = true;
					}
				}
				if (table.isDivergency()) {
					isDiffDivergency = true;
				}
				if (table.getLabel() == null || "".equals(table.getLabel())) {
					isDiffGray = true;
				}
			}
		} else if (type.equals(RepositoryNodeType.TABLE)) {
			MetadataTableRepositoryObject object = (MetadataTableRepositoryObject) node
					.getObject();
			MetadataTable table = object.getTable();
			List<MetadataColumn> columns = table.getColumns();
			for (MetadataColumn column : columns) {
				if (column.isDivergency()) {
					isDiffDivergency = true;
				}
				if (column.isSynchronised()) {
					isDiffSyschronize = true;
				}
				if (column.getLabel() == null || "".equals(column.getLabel())) {
					isDiffGray = true;
				}
			}
		}

		return new boolean[] { isDiffGray, isDiffDivergency, isDiffSyschronize };
	}

	/**
	 * DOC dev Comment method "removeAllRepositoryNodes".
	 */
	public void removeAllRepositoryNodes() {
		repositoryNodes.clear();
	}

	/**
	 * DOC dev Comment method "addRepositoryNode".
	 * 
	 * @param node
	 */
	public void addRepositoryNode(RepositoryNode node) {
		if (repositoryNodes.contains(node)) {
			return;
		}
		repositoryNodes.add(node);
	}

	/**
	 * DOC dev Comment method "getRepositoryNodebyName".
	 * 
	 * @param name
	 * @return
	 */
	public RepositoryNode getRepositoryNodebyName(String name) {
		for (RepositoryNode node : repositoryNodes) {
			if (((SqlBuilderRepositoryObject) node.getObject())
					.getRepositoryName().equals(name)) {
				return node;
			}
		}
		return null;
	}

	/**
	 * DOC dev Comment method "removeRepositoryNodeExceptNodeByName".
	 * 
	 * @param repositoryNodeName
	 */
	public void removeRepositoryNodeExceptNodeByName(String repositoryNodeName) {
		for (Iterator it = repositoryNodes.iterator(); it.hasNext();) {
			RepositoryNode node = (RepositoryNode) it.next();
			if (!((SqlBuilderRepositoryObject) node.getObject())
					.getRepositoryName().equals(repositoryNodeName)) {
				it.remove();
			}
		}
	}

	/**
	 * DOC dev Comment method "reductionALLRepositoryNode".
	 */
	@SuppressWarnings("unchecked")
	public static void reductionALLRepositoryNode() {
//		System.out.println("In reduction All Repository: isAction " + isAction+", isIncrease "+isIncrease);
		isFirst = false;
//		if (!isAction) {
			for (RepositoryNode node : repositoryNodes) {
				reductionOneRepositoryNode(node);
				
			}
			if (isDialogClosed) {
				repositoryNodes.clear();
				labelsAndNames.clear();
				oldMetaData.clear();
			}
			isDialogClosed = false;
//		}
	}

	/**
	 * DOC dev Comment method "reductionOneRepositoryNode".
	 * 
	 * @param node
	 */
	public static void reductionOneRepositoryNode(RepositoryNode node) {
		isReduction = true;
		DatabaseConnection connection = (DatabaseConnection) getItem(node)
				.getConnection();
		currentNodeLabel = getRoot(node).getObject().getLabel();
		reductionOneConnection(connection);
	}

	/**
	 * DOC dev Comment method "reductionOneConnection".
	 * 
	 * @param connection
	 */
	@SuppressWarnings("unchecked")
	public static void reductionOneConnection(DatabaseConnection connection) {

		Map<MetadataTable, List<MetadataColumn>> oldtableColumns = new HashMap<MetadataTable, List<MetadataColumn>>();
		List<MetadataTable> tables = connection.getTables();
		List<MetadataTable> newtables = new ArrayList<MetadataTable>();
		String connectionLabel = "Connections: " + currentNodeLabel;
		for (MetadataTable table : tables) {
			String connAndTableLabel = connectionLabel + "Tables: " + table.getLabel();
			List<MetadataColumn> oldcloumns = table.getColumns();
			List<MetadataColumn> newcloumns = new ArrayList<MetadataColumn>();
			List<MetadataColumn> oldCloumns = new ArrayList<MetadataColumn>();
			for (MetadataColumn column : oldcloumns) {
				oldCloumns.add(column);
				if (!column.getLabel().equals("")) {
					if (column.getOriginalField() != null && column.getOriginalField().equals(" ")) {
						column.setOriginalField(labelsAndNames.get(connAndTableLabel + "Columns: "
								+ column.getLabel()));
					}
					if (isDialogClosed) {
						column.setDivergency(false);
						column.setSynchronised(false);
					}
					newcloumns.add(column);
				}
			}
			table.getColumns().clear();
			table.getColumns().addAll(newcloumns);
			if (!table.getLabel().equals("")) {
				if (table.getSourceName() != null && table.getSourceName().equals(" ")) {
					table.setSourceName(labelsAndNames.get(connAndTableLabel));
				}
				if (isDialogClosed) {
					table.setDivergency(false);
					table.setSynchronised(false);
				}
				newtables.add(table);
			}
			oldtableColumns.put(table, oldCloumns);
		}
		oldMetaData.put(connection, oldtableColumns);
		connection.getTables().clear();
		connection.getTables().addAll(newtables);
		if (isDialogClosed) {
			connection.setDivergency(false);
			connection.setSynchronised(false);
		}
	}

	/**
	 * DOC dev Comment method "increaseALLRepositoryNode".
	 */
	public static void increaseALLRepositoryNode() {
//		System.out.println("In Increase All Repository: isAction " + isAction+", isIncrease "+isIncrease);
		if (!isFirst && !isIncrease) {
			if (repositoryNodes != null) {
				for (RepositoryNode node : repositoryNodes) {
					increaseOneRepositoryNode(node);
				}
			}
		}
		
	}

	/**
	 * DOC dev Comment method "increaseOneRepositoryNode".
	 * 
	 * @param node
	 */
	public static void increaseOneRepositoryNode(RepositoryNode node) {
		DatabaseConnection connection = (DatabaseConnection) getItem(node)
				.getConnection();
		increaseOneConnection(connection);
	}

	/**
	 * DOC dev Comment method "increaseOneConnection".
	 * 
	 * @param connection
	 */
	@SuppressWarnings("unchecked")
	public static void increaseOneConnection(DatabaseConnection connection) {
		if (oldMetaData != null && !oldMetaData.isEmpty()) {
			Map<MetadataTable, List<MetadataColumn>> oldtableColumns = oldMetaData
					.get(connection);
			if (oldtableColumns != null) {
				connection.getTables().clear();
				Set<MetadataTable> set = oldtableColumns.keySet();
				List<MetadataTable> sortTables = sortTableColumn(set);
				for (MetadataTable table : sortTables) {
					List<MetadataColumn> columns = oldtableColumns.get(table);
					List<MetadataColumn> newcolumns = new ArrayList<MetadataColumn>();
					for (MetadataColumn column : columns) {
						newcolumns.add(column);
					}
					table.getColumns().clear();
					table.getColumns().addAll(newcolumns);
					connection.getTables().add(table);
				}
			}
		}
		oldMetaData.clear();
	}

	/**
	 * DOC dev Comment method "sortTableColumn".
	 * @param set
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static List<MetadataTable> sortTableColumn(Collection<MetadataTable> set) {
        List<MetadataTable> sysTables = new ArrayList<MetadataTable>();
        List<MetadataTable> divTables = new ArrayList<MetadataTable>();
        List<MetadataTable> grayTables = new ArrayList<MetadataTable>();
        List<MetadataTable> norTables = new ArrayList<MetadataTable>();
        List<MetadataTable> sortTables = new ArrayList<MetadataTable>();
        for (MetadataTable object : set) {
            boolean isTableNormal = true;
            if (object.isDivergency()) {
                divTables.add(object);
                isTableNormal = false;
                continue;
            }
            if (object.getLabel() == null || "".equals(object.getLabel())) {
                grayTables.add(object);
                isTableNormal = false;
                continue;
            }
            List<MetadataColumn> columns = sortColumn(object.getColumns());
            object.getColumns().clear();
            object.getColumns().addAll(columns);
            for (MetadataColumn column : columns) {
                if (column.isSynchronised()) {
                    sysTables.add(object);
                    isTableNormal = false;
                    break;
                }
                if (column.isDivergency()) {
                    divTables.add(object);
                    isTableNormal = false;
                    break;
                }
                if (column.getLabel() == null || "".equals(column.getLabel())) {
                    grayTables.add(object);
                    isTableNormal = false;
                    break;
                }
            }
            if (isTableNormal) {
                norTables.add(object);
            }
        }
        MetadataTable[] norTablesArray = new MetadataTable[norTables.size()];
        norTablesArray = norTables.toArray(norTablesArray);
        MetadataTable[] sysTablesArray = new MetadataTable[sysTables.size()];
        sysTablesArray = sysTables.toArray(sysTablesArray);
        MetadataTable[] divTablesArray = new MetadataTable[divTables.size()];
        divTablesArray = divTables.toArray(divTablesArray);
        MetadataTable[] grayTablesArray = new MetadataTable[grayTables.size()];
        grayTablesArray = grayTables.toArray(grayTablesArray);
        MetadataTableComparator metadataTableComparator = new MetadataTableComparator();
        Arrays.sort(norTablesArray, metadataTableComparator);
        Arrays.sort(sysTablesArray, metadataTableComparator);
        Arrays.sort(divTablesArray, metadataTableComparator);
        Arrays.sort(grayTablesArray, metadataTableComparator);
        for (MetadataTable table : norTablesArray) {
            sortTables.add(table);
        }
        for (MetadataTable table : sysTablesArray) {
            sortTables.add(table);
        }
        for (MetadataTable table : divTablesArray) {
            sortTables.add(table);
        }
        for (MetadataTable table : grayTablesArray) {
            sortTables.add(table);
        }
        return sortTables;
    }
    /**
     * DOC dev Comment method "sortColumn".
     * @param columns
     * @return
     */
    private static List<MetadataColumn> sortColumn(Collection<MetadataColumn> columns) {
        List<MetadataColumn> sortColumns = new ArrayList<MetadataColumn>();
        List<MetadataColumn> sysColumns = new ArrayList<MetadataColumn>();
        List<MetadataColumn> grayColumns = new ArrayList<MetadataColumn>();
        List<MetadataColumn> divColumns = new ArrayList<MetadataColumn>();
        List<MetadataColumn> norColumns = new ArrayList<MetadataColumn>();
        for (MetadataColumn column : columns) {
            if (column.isSynchronised()) {
               sysColumns.add(column);
               continue;
            }
            if (column.isDivergency()) {
                divColumns.add(column);
                continue;
            }
            if (column.getLabel() == null || "".equals(column.getLabel())) {
                grayColumns.add(column);
                continue;
            }
            norColumns.add(column);
        }
        MetadataColumn[] norColumnsArray = new MetadataColumn[norColumns.size()];
        norColumnsArray = norColumns.toArray(norColumnsArray);
        MetadataColumn[] sysColumnsArray = new MetadataColumn[sysColumns.size()];
        sysColumnsArray = sysColumns.toArray(sysColumnsArray);
        MetadataColumn[] divColumnsArray = new MetadataColumn[divColumns.size()];
        divColumnsArray = divColumns.toArray(divColumnsArray);
        MetadataColumn[] grayColumnsArray = new MetadataColumn[grayColumns.size()];
        grayColumnsArray = grayColumns.toArray(grayColumnsArray);
        MetadataColumnComparator metadataColumnComparator = new MetadataColumnComparator();
        Arrays.sort(norColumnsArray, metadataColumnComparator);
        Arrays.sort(sysColumnsArray, metadataColumnComparator);
        Arrays.sort(divColumnsArray, metadataColumnComparator);
        Arrays.sort(grayColumnsArray, metadataColumnComparator);
        for (MetadataColumn column : norColumnsArray) {
            sortColumns.add(column);
        }
        for (MetadataColumn column : sysColumnsArray) {
            sortColumns.add(column);
        }
        for (MetadataColumn column : divColumnsArray) {
            sortColumns.add(column);
        }
        for (MetadataColumn column : grayColumnsArray) {
            sortColumns.add(column);
        }
        return sortColumns;
    }

	/**
	 * method "getTableNamesByRepositoryNode" get All Table Names in current
	 * RepositoryNode's DatabaseConnectionItem.
	 * 
	 * @param node
	 *            current RepositoryNode
	 * @return List :all Table Names.
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, List<String>> getAllNamesByRepositoryNode(
			RepositoryNode node) {
		Map<String, List<String>> allNames = new HashMap<String, List<String>>();
		DatabaseConnectionItem item = getItem(getRoot(node));
		DatabaseConnection connection = (DatabaseConnection) item
				.getConnection();
		List<MetadataTable> tablesFromEMF = connection.getTables();
		boolean isOdbc = connection.getSID() == null
				|| connection.getSID().length() == 0;
		String sid = isOdbc ? connection.getDatasourceName() : connection
				.getSID();
		for (MetadataTable table : tablesFromEMF) {
			String tableName = table.getSourceName();
			if (tableName != null && !"".equals(tableName)) {
				List<String> columnNames = new ArrayList<String>();
				tableName = "\"" + sid + "\".\"" + tableName + "\"";
				List<MetadataColumn> columns = table.getColumns();
				for (MetadataColumn column : columns) {
					String columnName = column.getOriginalField();
					if (columnName != null && !"".equals(columnName)) {
						columnName = tableName + ".\"" + columnName + "\"";
						columnNames.add(columnName);
					}
				}

				allNames.put(tableName, columnNames);
			}
		}
		return allNames;
	}

	/**
	 * method "getALLReposotoryNodeNames" get all DatabaseConnection's
	 * RepositoryNode Names.
	 * 
	 * @return List : all DatabaseConnection's RepositoryNode Names
	 */
	public List<String> getALLReposotoryNodeNames() {
		List<String> names = new ArrayList<String>();
		for (RepositoryNode node : repositoryNodes) {
			names.add(((SqlBuilderRepositoryObject) node.getObject())
					.getRepositoryName());
		}
		return names;
	}

	/**
	 * method "getRepositoryNodeFromDB".
	 * 
	 * @param oldNode
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public RepositoryNode getRepositoryNodeFromDB(RepositoryNode oldNode) {
		if (!isReduction) {
			currentNodeLabel = oldNode.getObject().getLabel();
			DatabaseConnectionItem item = getItem(getRoot(oldNode));

			DatabaseConnection connection = (DatabaseConnection) item
					.getConnection();
			IMetadataConnection iMetadataConnection = ConvertionHelper
					.convert(connection);
			modifyOldRepositoryNode(connection, iMetadataConnection);

		}
		return oldNode;
	}

	/**
	 * DOC dev Comment method "modifyOldRepositoryNode".
	 * 
	 * @param connection
	 * @param iMetadataConnection
	 */
	@SuppressWarnings("unchecked")
	private void modifyOldRepositoryNode(DatabaseConnection connection,
			IMetadataConnection iMetadataConnection) {
		boolean status = new ManagerConnection().check(iMetadataConnection);
		connection.setDivergency(!status);
		if (status) {
			// /Get MetadataTable From DB
			List<MetadataTable> tablesFromDB = getTablesFromDB(iMetadataConnection);
			// Get MetadataTable From EMF(Old RepositoryNode)
			List<MetadataTable> tablesFromEMF = connection.getTables();
			String connectionLabel = "Connections: " + currentNodeLabel;
			for (MetadataTable tableFromDB : tablesFromDB) {
				// /Get MetadataColumn from DB
				List<MetadataColumn> columnsFromDB = getColumnsFromDB(
						iMetadataConnection, tableFromDB);
				for (MetadataTable tableFromEMF : tablesFromEMF) {
					// /Get MetadataColumn From EMF
					String connAndTableLabel = connectionLabel + "Tables: " + tableFromEMF.getLabel();
					List<MetadataColumn> columnsFromEMF = tableFromEMF
							.getColumns();
					if (tableFromDB.getSourceName().equals(
							tableFromEMF.getSourceName())) {
						fixedColumns(columnsFromDB, columnsFromEMF, connAndTableLabel);
					}
				}
			}
			fixedTables(tablesFromDB, tablesFromEMF, iMetadataConnection, connectionLabel);
			tablesFromEMF = sortTableColumn(tablesFromEMF);
            connection.getTables().clear();
            connection.getTables().addAll(tablesFromEMF);
		} else {
			List<MetadataTable> tablesFromEMF = connection.getTables();
			String connectionLabel = "Connections: " + currentNodeLabel;
			for (MetadataTable tableFromEMF : tablesFromEMF) {
				List<MetadataColumn> columnsFromEMF = tableFromEMF.getColumns();
				String connAndTableLabel = connectionLabel + "Tables: " + tableFromEMF.getLabel();
				for (MetadataColumn column : columnsFromEMF) {
					labelsAndNames.put(connAndTableLabel + "Columns: " + column.getLabel(), column
							.getOriginalField());
					column.setOriginalField(" ");
					column.setDivergency(true);
					column.setSynchronised(false);
				}
				labelsAndNames.put(connAndTableLabel,
						tableFromEMF.getSourceName());
				tableFromEMF.setSourceName(" ");
				tableFromEMF.setDivergency(true);
			}
		}
	}

	/**
	 * DOC dev Comment method "getRepositoryNodeByBuildIn".
	 * 
	 * @param node
	 * @param parameters
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public RepositoryNode getRepositoryNodeByBuildIn(RepositoryNode node,
			ConnectionParameters parameters) {
        if (parameters.getSchema().equals("\'\'")) {
            parameters.setConnectionComment("Please Input Schema !");
            return null;
        }
		DatabaseConnection connection = createConnection(parameters);
		IMetadataConnection iMetadataConnection = ConvertionHelper
				.convert(connection);

		RepositoryNode newNode = createNewRepositoryNode(node, parameters,
				connection, iMetadataConnection);

		return newNode;
	}

	/**
	 * DOC dev Comment method "createNewRepositoryNode".
	 * 
	 * @param node
	 * @param parameters
	 * @param connection
	 * @param iMetadataConnection
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private RepositoryNode createNewRepositoryNode(RepositoryNode node,
			ConnectionParameters parameters, DatabaseConnection connection,
			IMetadataConnection iMetadataConnection) {
		ManagerConnection managerConnection = new ManagerConnection();
		boolean status = managerConnection.check(iMetadataConnection);
		connection.setDivergency(!status);
		connection.getTables().clear();
		if (status) {
			List<MetadataTable> tablesFromDB = getTablesFromDB(iMetadataConnection);
			for (MetadataTable table : tablesFromDB) {
				List<MetadataColumn> columnsFromDB = getColumnsFromDB(
						iMetadataConnection, table);
				table.getColumns().clear();
				for (MetadataColumn column : columnsFromDB) {
					column.setLabel("");
					table.getColumns().add(column);
				}
				table.setLabel("");
				connection.getTables().add(table);
			}
		} else {
			if (parameters != null) {
				parameters.setConnectionComment(managerConnection
						.getMessageException());
			}
		}
		DatabaseConnectionItem item = PropertiesFactory.eINSTANCE
				.createDatabaseConnectionItem();
		Property connectionProperty = PropertiesFactory.eINSTANCE
				.createProperty();
		connectionProperty.setAuthor(((RepositoryContext) CorePlugin
				.getContext().getProperty(Context.REPOSITORY_CONTEXT_KEY))
				.getUser());
		connectionProperty.setVersion(VersionUtils.DEFAULT_VERSION);
		connectionProperty.setStatusCode("");

		item.setProperty(connectionProperty);
		item.setConnection(connection);
		RepositoryObject object = new RepositoryObject(connectionProperty);
		object.setLabel("");
		ItemState state = PropertiesFactory.eINSTANCE.createItemState();
		state.setDeleted(false);
		item.setState(state);
		if (node == null) {
			node = new RepositoryNode(null, null, ENodeType.SYSTEM_FOLDER);
		}
		RepositoryNode newNode = new RepositoryNode(object, node,
				ENodeType.SYSTEM_FOLDER);
		return newNode;
	}

	/**
	 * DOC dev Comment method "getColumnsFromDB".
	 * 
	 * @param iMetadataConnection
	 * @param table
	 * @return
	 */
	private List<MetadataColumn> getColumnsFromDB(
			IMetadataConnection iMetadataConnection, MetadataTable table) {
		List<MetadataColumn> metadataColumns = new ArrayList<MetadataColumn>();

		try {
			DatabaseMetaData dbMetaData = getDatabaseMetaData(iMetadataConnection);

			IMetadataTable metaTable1 = new org.talend.core.model.metadata.MetadataTable();
			metaTable1.setLabel(table.getLabel());
			metaTable1.setTableName(table.getSourceName());
			metadataColumns = ExtractMetaDataFromDataBase
					.extractMetadataColumnsFormTable(dbMetaData, metaTable1,
							iMetadataConnection.getDbType());
			ExtractMetaDataUtils.closeConnection();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return metadataColumns;
	}

	/**
	 * DOC dev Comment method "createConnection".
	 * 
	 * @param parameters
	 *            inputed when use Built-In .
	 * @return DatabaseConnection : connetion .
	 */
	public DatabaseConnection createConnection(ConnectionParameters parameters) {
		DatabaseConnection connection = ConnectionFactory.eINSTANCE
				.createDatabaseConnection();
		connection.setDatabaseType(parameters.getDbType());
		connection.setUsername(parameters.getUserName());
		connection.setPort(parameters.getPort());
		connection.setPassword(parameters.getPassword());
		connection.setSID(parameters.getDbName());
		connection.setLabel(parameters.getDbName());
		connection.setDatasourceName(parameters.getDatasource());
		if (parameters.getSchema() != null) {
             connection.setSchema(parameters.getSchema().replaceAll("\'", ""));
		}
		connection.setURL(parameters.getURL());
		connection.setDriverClass(ExtractMetaDataUtils
				.getDriverClassByDbType(parameters.getDbType()));
		return connection;
	}

	/**
	 * DOC dev Comment method "getDbTypeFromRepositoryNode".
	 * 
	 * @param node
	 * @return
	 */
	public static String getDbTypeFromRepositoryNode(RepositoryNode node) {
		DatabaseConnection connection = (DatabaseConnection) getItem(
				getRoot(node)).getConnection();
		return connection.getDatabaseType();
	}

	/**
	 * method "getItem" get DatabaseConnectionItem by current RepositoryNode .
	 * 
	 * @param newNode
	 *            current RepositoryNode
	 * @return DatabaseConnectionItem : item current node.
	 */
	public static DatabaseConnectionItem getItem(RepositoryNode newNode) {
		IRepositoryObject repositoryObject = newNode.getObject();
		DatabaseConnectionItem item = (DatabaseConnectionItem) repositoryObject
				.getProperty().getItem();
		return item;
	}

	/**
	 * method "getDatabaseNameByRepositoryNode" get databaseName .
	 * 
	 * @param node
	 *            current RepositoryNode
	 * @return String :databaseName
	 */
	public static String getDatabaseNameByRepositoryNode(RepositoryNode node) {
		DatabaseConnection connection = (DatabaseConnection) getItem(node)
				.getConnection();
		boolean isOdbc = connection.getSID() == null
				|| connection.getSID().length() == 0;
		return isOdbc ? connection.getDatasourceName() : connection.getSID();
	}

	/**
	 * method "getTablesFromDB" get all tables from DataBase.
	 * 
	 * @param iMetadataConnection
	 *            contains connection
	 * @return all Tables from Database.
	 */
	private List<MetadataTable> getTablesFromDB(
			IMetadataConnection iMetadataConnection) {

		DatabaseMetaData dbMetaData = getDatabaseMetaData(iMetadataConnection);

		List<MetadataTable> metadataTables = new ArrayList<MetadataTable>();
		try {
			String[] tableTypes = { "TABLE", "VIEW" };
			ResultSet rsTables = null;
			rsTables = dbMetaData.getTables(null, ExtractMetaDataUtils.schema,
					null, tableTypes);
			while (rsTables.next()) {
				MetadataTable medataTable = ConnectionFactory.eINSTANCE
						.createMetadataTable();
				medataTable.setId(metadataTables.size() + 1 + "");
				medataTable.setLabel(rsTables.getString("TABLE_NAME"));
				medataTable.setSourceName(medataTable.getLabel());
				medataTable.setComment(ExtractMetaDataUtils
						.getStringMetaDataInfo(rsTables, "REMARKS"));
				metadataTables.add(medataTable);
			}
			rsTables.close();
			ExtractMetaDataUtils.closeConnection();
		} catch (Exception e) {
			SqlBuilderPlugin.log("Connection Exception", e);
			throw new RuntimeException(e);
		}
		return metadataTables;
	}

	/**
	 * method "getDatabaseMetaData" get databaseMetaData.
	 * 
	 * @param iMetadataConnection
	 *            contains connection
	 * @return dbMetaData DatabaseMetaData .
	 */
	private DatabaseMetaData getDatabaseMetaData(
			IMetadataConnection iMetadataConnection) {
		ExtractMetaDataUtils.getConnection(iMetadataConnection.getDbType(),
				iMetadataConnection.getUrl(),
				iMetadataConnection.getUsername(), iMetadataConnection
						.getPassword(), iMetadataConnection.getDatabase(),
				iMetadataConnection.getSchema());
		DatabaseMetaData dbMetaData = ExtractMetaDataUtils
				.getDatabaseMetaData(ExtractMetaDataUtils.conn);
		return dbMetaData;
	}

	/**
	 * DOC dev Comment method "getALLQueryLabels".
	 * 
	 * @param repositoryNode
	 *            current RepositoryNode.
	 * @return all QueryLabels in Emf.
	 */
	@SuppressWarnings("unchecked")
	public List<String> getALLQueryLabels(RepositoryNode repositoryNode) {
		List<String> allQueries = new ArrayList<String>();
		DatabaseConnectionItem item = getItem(repositoryNode);
		DatabaseConnection connection = (DatabaseConnection) item
				.getConnection();
		List<QueriesConnection> qcs = connection.getQueries();
		if (!qcs.isEmpty()) {
			QueriesConnection connection2 = qcs.get(0);
			List<Query> qs = connection2.getQuery();
			for (Query q1 : qs) {
				allQueries.add(q1.getLabel());
			}
		}
		return allQueries;
	}

	/**
	 * method "saveQuery" use save inputed Query to EMF's xml File.
	 * @param repositoryNode
	 *            current RepositoryNode
	 * @param query
	 *            need to save Query
	 */
	@SuppressWarnings("unchecked")
	public void saveQuery(RepositoryNode repositoryNode, Query query) {
		DatabaseConnectionItem item = getItem(repositoryNode);
		if (query != null) {
			DatabaseConnection connection = (DatabaseConnection) item
					.getConnection();
			List<QueriesConnection> list = connection.getQueries();
			if (list.isEmpty()) {
				QueriesConnection qc = ConnectionFactory.eINSTANCE
						.createQueriesConnection();
				qc.setConnection(connection);
				qc.getQuery().add(query);
				connection.getQueries().add(qc);
			} else {
				QueriesConnection connection2 = list.get(0);
				List<Query> queries = connection2.getQuery();
				boolean isModify = false;
				for (Query query2 : queries) {
					if (query2.getLabel().equals(query.getLabel())) {
						query2.setComment(query.getComment());
						query2.setId(query.getId());
						query2.setValue(query.getValue());
						isModify = true;
					}
				}
				if (!isModify) {
					connection2.getQuery().add(query);
				}
			}
		}
		saveMetaData(item);
	}

	/**
	 * method "deleteQueries" use delete Queries.
	 * @param repositoryNode
	 *            databaseConnection's RepositoryNode
	 * @param queries
	 *            need to deleted Queries
	 */
	@SuppressWarnings("unchecked")
	public void deleteQueries(RepositoryNode repositoryNode, List<Query> queries) {
		DatabaseConnectionItem item = getItem(repositoryNode);
		DatabaseConnection connection = (DatabaseConnection) item
				.getConnection();
		List<QueriesConnection> list = connection.getQueries();
		if (!list.isEmpty()) {
			QueriesConnection connection2 = list.get(0);
			List<Query> qs = connection2.getQuery();
			List<Query> qs2 = new ArrayList<Query>();
			qs2.clear();
			for (Query query : qs) {
				boolean flag = true;
				for (Query q : queries) {
					if (query.getLabel().equals(q.getLabel())) {
						flag = false;
					}
				}
				if (flag) {
					qs2.add(query);
				}
			}
			connection2.getQuery().clear();
			connection2.getQuery().addAll(qs2);
		}
		saveMetaData(item);
	}

	/**
	 * save MetaData into EMF's xml files.
	 * @param item
	 *            need to be saved Item
	 */
	public void saveMetaData(DatabaseConnectionItem item) {

		ProxyRepositoryFactory factory = ProxyRepositoryFactory.getInstance();
		try {
			factory.save(item);
		} catch (PersistenceException e) {
			SqlBuilderPlugin.log("Save MetaData Failure", e);
		}
	}

	/**
	 * fixed Table .
	 * @param metaFromDB
	 *            MetadataTable from Database
	 * @param metaFromEMF
	 *            MetadataTable from Emf
	 * @param iMetadataConnection
	 *            contain Connection.
	 */
	@SuppressWarnings("unchecked")
	private void fixedTables(List<MetadataTable> metaFromDB,
			List<MetadataTable> metaFromEMF,
			IMetadataConnection iMetadataConnection, String connectionLabel) {
		for (MetadataTable emf : metaFromEMF) {
			boolean flag = true;
			for (MetadataTable db : metaFromDB) {
				if (db.getSourceName().equals(emf.getSourceName())) {
					flag = false;
					break;
				}
			}
			if (flag) {
				String connAndTableLabel = connectionLabel + "Tables: " + emf.getLabel();
				List<MetadataColumn> columns = emf.getColumns();
				for (MetadataColumn column : columns) {
					labelsAndNames.put(connAndTableLabel + "Columns: " + column.getLabel(), column.getOriginalField());
					column.setOriginalField(" ");
					column.setDivergency(true);
					column.setSynchronised(false);
				}
				labelsAndNames.put(connAndTableLabel, emf.getSourceName());
				emf.setSourceName(" ");
				emf.setDivergency(true);
				emf.setSynchronised(false);
			}
		}
		while (!metaFromDB.isEmpty()) {
			boolean flag = true;
			MetadataTable db = metaFromDB.remove(0);
			for (MetadataTable emf : metaFromEMF) {
				if (db.getSourceName().equals(emf.getSourceName())) {
					flag = false;
					if (!emf.getLabel().equals("")
							&& !emf.getLabel().equals(
									db.getSourceName().replaceAll("_", "-"))) {
						emf.setDivergency(true);
					}
				}
			}
			if (flag) {
				MetadataTable table = ConnectionFactory.eINSTANCE
						.createMetadataTable();
				table.setSourceName(db.getSourceName());
				table.setLabel("");
				List<MetadataColumn> columns = getColumnsFromDB(
						iMetadataConnection, db);
				for (MetadataColumn column : columns) {
					MetadataColumn column1 = ConnectionFactory.eINSTANCE
							.createMetadataColumn();
					column1.setOriginalField(column.getOriginalField());
					column1.setLabel("");
					table.getColumns().add(column1);
				}
				metaFromEMF.add(table);
			}
		}
	}

	/**
	 * fixed Column from EMF use Column From DataBase .
	 * @param columnsFromDB
	 *            MetadataColumn from Database
	 * @param cloumnsFromEMF
	 *            MetadataColumn from Emf
	 */
	private void fixedColumns(List<MetadataColumn> columnsFromDB,
			List<MetadataColumn> cloumnsFromEMF, String connAndTableLabel) {
		for (MetadataColumn emf : cloumnsFromEMF) {
			boolean flag = true;
			for (MetadataColumn db : columnsFromDB) {

				if (db.getOriginalField().equals(emf.getOriginalField())) {
					flag = false;
					break;
				}
			}
			if (flag) {
				labelsAndNames.put(connAndTableLabel + "Columns: " + emf.getLabel(), emf.getOriginalField());
				emf.setOriginalField(" ");
				emf.setDivergency(true);
				emf.setSynchronised(false);
			}
		}
		while (!columnsFromDB.isEmpty()) {
			boolean flag = true;
			MetadataColumn db = columnsFromDB.remove(0);
			for (MetadataColumn emf : cloumnsFromEMF) {
				if (db.getOriginalField().equals(emf.getOriginalField())) {
					flag = false;
					if (emf.getLabel().length() != 0) {
						boolean is = !isEquivalent(db, emf);
						emf.setDivergency(is);
						emf.setSynchronised(is);
					}
				}
			}
			if (flag) {
				MetadataColumn column = ConnectionFactory.eINSTANCE
						.createMetadataColumn();
				column.setOriginalField(db.getOriginalField());
				column.setLabel("");
				cloumnsFromEMF.add(column);
			}
		}

	}

	/**
	 * Check if Two MetadataColumns are the same..
	 * @param info
	 *            MetadataColumn
	 * @param column
	 *            MetadataColumn
	 * @return isEquivalent.
	 */
	private boolean isEquivalent(MetadataColumn info, MetadataColumn column) {

		if (info.getLength() != column.getLength()) {
			return false;
		}
		if (info.getDefaultValue() != null
				&& !info.getDefaultValue().equals(column.getDefaultValue())) {
			return false;
		}
		if (column.getDefaultValue() != null
				&& column.getDefaultValue().length() != 0
				&& !column.getDefaultValue().equals(info.getDefaultValue())) {
			return false;
		}
		if (info.isNullable() != column.isNullable()) {
			return false;
		}
		if (info.isKey() != column.isKey()) {
			return false;
		}
		if (info.getPrecision() != column.getPrecision()) {
			return false;
		}
		if (info.getSourceType() != null
				&& !info.getSourceType().equals(column.getSourceType())) {
			return false;
		}
		if (info.getComment() != null && info.getComment().length() != 0
				&& !info.getComment().equals(column.getComment())) {
			return false;
		}
		if (column.getComment() != null && column.getComment().length() != 0
				&& !column.getComment().equals(info.getComment())) {
			return false;
		}

		if (info.getTalendType() != null
				&& !info.getTalendType().equals(column.getTalendType())) {
			return false;
		}
		if (column.getTalendType() != null
				&& !column.getTalendType().equals(info.getTalendType())) {
			return false;
		}

		return true;
	}

	/**
	 * RepositoryNode.
	 * @param repositoryNode
	 *            RepositoryNode
	 * @return RepositoryNodeType
	 * @see RepositoryNodeType
	 */
	public static RepositoryNodeType getRepositoryType(
			RepositoryNode repositoryNode) {
		return (RepositoryNodeType) repositoryNode
				.getProperties(EProperties.CONTENT_TYPE);
	}

	/**
	 * be a RepositoryNode with database infomation.
	 * 
	 * @param repositoryNode
	 *            RepositoryNode
	 * @return RepositoryNode
	 */
	public static RepositoryNode getRoot(RepositoryNode repositoryNode) {
		if (getRepositoryType(repositoryNode) == RepositoryNodeType.FOLDER) {
			throw new RuntimeException(
					"RepositoryNode with folder info should not call this.");
		}

		if (getRepositoryType(repositoryNode) == RepositoryNodeType.DATABASE) {
			return repositoryNode;
		}
		return getRoot(repositoryNode.getParent());
	}

	/**
	 * Getter for isDialogClosed.
	 * @return the isDialogClosed
	 */
	public static boolean isDialogClosed() {
		return isDialogClosed;
	}

	/**
	 * Sets the isDialogClosed.
	 * @param isDialogClosed the isDialogClosed to set
	 */
	public static void setDialogClosed(boolean isDialogClosed2) {
		SQLBuilderRepositoryNodeManager.isDialogClosed = isDialogClosed2;
	}

	/**
	 * Getter for isIncrease.
	 * @return the isIncrease
	 */
	public static boolean isIncrease() {
		return isIncrease;
	}

	/**
	 * Sets the isIncrease.
	 * @param isIncrease the isIncrease to set
	 */
	public static void setIncrease(boolean isIncrease2) {
		SQLBuilderRepositoryNodeManager.isIncrease = isIncrease2;
	}

	/**
	 * Getter for isReduction.
	 * @return the isReduction
	 */
	public static boolean isReduction() {
		return isReduction;
	}

	/**
	 * Sets the isReduction.
	 * @param isReduction the isReduction to set
	 */
	public static void setReduction(boolean isReduction2) {
		SQLBuilderRepositoryNodeManager.isReduction = isReduction2;
	}
	
}

/**
 * DOC dev  class global comment. Detailled comment
 * <br/>
 *
 * $Id: talend-code-templates.xml 1 2006-09-29 17:06:40 +0000 (Fri, 29 Sep 2006) nrousseau $
 *
 */
class MetadataTableComparator implements Comparator<MetadataTable> {

	/* (non-Javadoc)
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
	 */
	public int compare(MetadataTable o1, MetadataTable o2) {
		if (o1.getSourceName() == null || "".equals(o1.getSourceName()) 
				|| " ".equals(o1.getSourceName()) || o2.getSourceName() == null) {
			return -1;
		} else {
			return o1.getSourceName().compareTo(o2.getSourceName());
		}
	}
}

/**
 * DOC dev  class global comment. Detailled comment
 * <br/>
 *
 * $Id: talend-code-templates.xml 1 2006-09-29 17:06:40 +0000 (Fri, 29 Sep 2006) nrousseau $
 *
 */
class MetadataColumnComparator implements Comparator<MetadataColumn> {
    public int compare(MetadataColumn o1, MetadataColumn o2) {
        if (o1.getOriginalField() == null || "".equals(o1.getOriginalField()) 
        		|| " ".equals(o1.getOriginalField()) || o2.getOriginalField() == null) {
            return -1;
        } else {
            return o1.getOriginalField().compareTo(o2.getOriginalField());
        }
    }
}
