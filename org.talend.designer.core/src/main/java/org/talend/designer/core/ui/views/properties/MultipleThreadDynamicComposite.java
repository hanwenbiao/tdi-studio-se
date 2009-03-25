// ============================================================================
//
// Copyright (C) 2006-2009 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.designer.core.ui.views.properties;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.BidiMap;
import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.gef.commands.CommandStackEvent;
import org.eclipse.gef.commands.CommandStackEventListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.internal.dialogs.EventLoopProgressMonitor;
import org.eclipse.ui.views.properties.tabbed.ITabbedPropertyConstants;
import org.talend.commons.exception.ExceptionHandler;
import org.talend.commons.exception.PersistenceException;
import org.talend.commons.ui.swt.dialogs.ProgressDialog;
import org.talend.commons.utils.threading.ExecutionLimiter;
import org.talend.core.model.metadata.IMetadataTable;
import org.talend.core.model.metadata.MetadataTalendType;
import org.talend.core.model.metadata.builder.ConvertionHelper;
import org.talend.core.model.metadata.builder.connection.Connection;
import org.talend.core.model.metadata.builder.connection.DatabaseConnection;
import org.talend.core.model.metadata.builder.connection.DelimitedFileConnection;
import org.talend.core.model.metadata.builder.connection.EbcdicConnection;
import org.talend.core.model.metadata.builder.connection.FileExcelConnection;
import org.talend.core.model.metadata.builder.connection.GenericSchemaConnection;
import org.talend.core.model.metadata.builder.connection.LDAPSchemaConnection;
import org.talend.core.model.metadata.builder.connection.PositionalFileConnection;
import org.talend.core.model.metadata.builder.connection.QueriesConnection;
import org.talend.core.model.metadata.builder.connection.Query;
import org.talend.core.model.metadata.builder.connection.RegexpFileConnection;
import org.talend.core.model.metadata.builder.connection.SAPConnection;
import org.talend.core.model.metadata.builder.connection.SalesforceSchemaConnection;
import org.talend.core.model.metadata.builder.connection.WSDLSchemaConnection;
import org.talend.core.model.metadata.builder.connection.XmlFileConnection;
import org.talend.core.model.metadata.designerproperties.RepositoryToComponentProperty;
import org.talend.core.model.process.EComponentCategory;
import org.talend.core.model.process.EConnectionType;
import org.talend.core.model.process.EParameterFieldType;
import org.talend.core.model.process.Element;
import org.talend.core.model.process.IElementParameter;
import org.talend.core.model.properties.ConnectionItem;
import org.talend.core.model.properties.Item;
import org.talend.core.model.repository.ERepositoryObjectType;
import org.talend.core.model.repository.IRepositoryObject;
import org.talend.core.properties.tab.IDynamicProperty;
import org.talend.designer.core.DesignerPlugin;
import org.talend.designer.core.i18n.Messages;
import org.talend.designer.core.model.components.EParameterName;
import org.talend.designer.core.ui.AbstractMultiPageTalendEditor;
import org.talend.designer.core.ui.ActiveProcessTracker;
import org.talend.designer.core.ui.editor.cmd.ChangeMetadataCommand;
import org.talend.designer.core.ui.editor.nodes.Node;
import org.talend.designer.core.ui.editor.process.Process;
import org.talend.designer.core.ui.editor.properties.controllers.AbstractElementPropertySectionController;
import org.talend.designer.core.ui.editor.properties.controllers.GroupController;
import org.talend.designer.core.ui.editor.subjobcontainer.SubjobContainer;
import org.talend.designer.core.ui.projectsetting.ImplicitContextLoadElement;
import org.talend.designer.core.ui.projectsetting.StatsAndLogsElement;
import org.talend.repository.model.ERepositoryStatus;
import org.talend.repository.model.IProxyRepositoryFactory;

/**
 * yzhang class global comment. Detailled comment <br/>
 * 
 * $Id: DynamicTabbedPropertySection.java 6579 2007-10-26 10:33:01Z ftang $
 * 
 */
public class MultipleThreadDynamicComposite extends ScrolledComposite implements IDynamicProperty {

    protected AbstractMultiPageTalendEditor part;

    protected Element elem;

    protected Composite parent;

    protected BidiMap hashCurControls;

    protected String currentComponent;

    protected EComponentCategory section;

    protected int curRowSize;

    protected DynamicPropertyGenerator generator;

    private final Map<String, IMetadataTable> repositoryTableMap;

    private final Map<String, ConnectionItem> repositoryConnectionItemMap;

    private final Map<String, Query> repositoryQueryStoreMap;

    private Map<String, String> tableIdAndDbTypeMap = new HashMap<String, String>();

    private Map<String, String> tableIdAndDbSchemaMap = new HashMap<String, String>();

    private boolean forceRedraw;

    private int lastCompositeSize = 0;

    private Process process;

    private boolean propertyResized;

    protected Composite composite;

    private final String updataComponentParamName;

    private boolean isCompactView;

    public String getRepositoryAliasName(ConnectionItem connectionItem) {
        ERepositoryObjectType repositoryObjectType = ERepositoryObjectType.getItemType(connectionItem);
        String aliasName = repositoryObjectType.getAlias();
        Connection connection = connectionItem.getConnection();
        if (connection instanceof DatabaseConnection) {
            String currentDbType = (String) RepositoryToComponentProperty.getValue(connection, "TYPE"); //$NON-NLS-1$
            aliasName += " (" + currentDbType + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        }
        return aliasName;
    }

    private void updateRepositoryList() {

        ProgressDialog progressDialog = new ProgressDialog(this.getShell(), 1000) {

            private IProgressMonitor monitorWrap;

            @Override
            public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                monitorWrap = new EventLoopProgressMonitor(monitor);
                IProxyRepositoryFactory factory = DesignerPlugin.getDefault().getProxyRepositoryFactory();

                List<IRepositoryObject> repositoryObjects;
                try {
                    repositoryObjects = factory.getAll(ERepositoryObjectType.METADATA);
                } catch (PersistenceException e) {
                    throw new RuntimeException(e);
                }

                int total = repositoryObjects.size(); // + elem.getElementParameters().size();
                monitorWrap.beginTask(Messages.getString("MultipleThreadDynamicComposite.gatherInformation"), total); //$NON-NLS-1$

                IElementParameter propertyParam = elem.getElementParameterFromField(EParameterFieldType.PROPERTY_TYPE, section);
                String repositoryValue = null;

                if (propertyParam != null) {
                    repositoryValue = propertyParam.getRepositoryValue();
                }

                if (repositoryObjects != null && (repositoryObjects.size() != 0)) {
                    repositoryTableMap.clear();
                    repositoryQueryStoreMap.clear();
                    repositoryConnectionItemMap.clear();
                    tableIdAndDbTypeMap.clear();
                    tableIdAndDbSchemaMap.clear();

                    for (IRepositoryObject curObject : repositoryObjects) {

                        Item item = curObject.getProperty().getItem();
                        if (item instanceof ConnectionItem) {
                            ConnectionItem connectionItem = (ConnectionItem) item;
                            Connection connection = connectionItem.getConnection();
                            if (connection.isReadOnly()) {
                                continue;
                            }

                            if (repositoryValue != null) {
                                if ((connection instanceof DelimitedFileConnection) && (repositoryValue.equals("DELIMITED"))) { //$NON-NLS-1$
                                    repositoryConnectionItemMap.put(connectionItem.getProperty().getId(), connectionItem);
                                }
                                if ((connection instanceof PositionalFileConnection) && (repositoryValue.equals("POSITIONAL"))) { //$NON-NLS-1$
                                    repositoryConnectionItemMap.put(connectionItem.getProperty().getId(), connectionItem);
                                }
                                if ((connection instanceof FileExcelConnection) && (repositoryValue.equals("EXCEL"))) { //$NON-NLS-1$
                                    repositoryConnectionItemMap.put(connectionItem.getProperty().getId(), connectionItem);
                                }
                                if ((connection instanceof EbcdicConnection) && (repositoryValue.equals("EBCDIC"))) { //$NON-NLS-1$
                                    repositoryConnectionItemMap.put(connectionItem.getProperty().getId(), connectionItem);
                                }
                                if ((connection instanceof RegexpFileConnection) && (repositoryValue.equals("REGEX"))) { //$NON-NLS-1$
                                    repositoryConnectionItemMap.put(connectionItem.getProperty().getId(), connectionItem);
                                }
                                if ((connection instanceof XmlFileConnection) && (repositoryValue.equals("XML"))) { //$NON-NLS-1$
                                    repositoryConnectionItemMap.put(connectionItem.getProperty().getId(), connectionItem);
                                }
                                if ((connection instanceof GenericSchemaConnection) && (repositoryValue.equals("GENERIC"))) { //$NON-NLS-1$
                                    repositoryConnectionItemMap.put(connectionItem.getProperty().getId(), connectionItem);
                                }
                                if ((connection instanceof LDAPSchemaConnection) && (repositoryValue.equals("LDAP"))) { //$NON-NLS-1$
                                    repositoryConnectionItemMap.put(connectionItem.getProperty().getId(), connectionItem);
                                }
                                if ((connection instanceof WSDLSchemaConnection) && (repositoryValue.equals("WSDL"))) { //$NON-NLS-1$
                                    repositoryConnectionItemMap.put(connectionItem.getProperty().getId(), connectionItem);
                                }
                                if ((connection instanceof SalesforceSchemaConnection) && (repositoryValue.equals("SALESFORCE"))) { //$NON-NLS-1$
                                    repositoryConnectionItemMap.put(connectionItem.getProperty().getId(), connectionItem);
                                }
                                if ((connection instanceof SAPConnection) && (repositoryValue.equals("SAP"))) { //$NON-NLS-1$
                                    repositoryConnectionItemMap.put(connectionItem.getProperty().getId(), connectionItem);
                                }
                                if ((connection instanceof DatabaseConnection) && (repositoryValue.startsWith("DATABASE"))) { //$NON-NLS-1$
                                    String currentDbType = (String) RepositoryToComponentProperty.getValue(connection, "TYPE"); //$NON-NLS-1$
                                    if (repositoryValue.contains(":")) { // database is specified //$NON-NLS-1$
                                        String neededDbType = repositoryValue.substring(repositoryValue.indexOf(":") + 1); //$NON-NLS-1$;
                                        if (MetadataTalendType.sameDBProductType(neededDbType, currentDbType)) {
                                            repositoryConnectionItemMap.put(connectionItem.getProperty().getId(), connectionItem);
                                        }
                                    } else {
                                        repositoryConnectionItemMap.put(connectionItem.getProperty().getId(), connectionItem);
                                    }
                                }
                            } else {
                                repositoryConnectionItemMap.put(connectionItem.getProperty().getId(), connectionItem);
                            }
                            for (Object tableObj : connection.getTables()) {
                                org.talend.core.model.metadata.builder.connection.MetadataTable table;

                                table = (org.talend.core.model.metadata.builder.connection.MetadataTable) tableObj;

                                if (factory.getStatus(connectionItem) != ERepositoryStatus.DELETED) {
                                    if (!factory.isDeleted(table)) {
                                        IMetadataTable newTable = ConvertionHelper.convert(table);
                                        repositoryTableMap.put(connectionItem.getProperty().getId() + " - " + table.getLabel(), //$NON-NLS-1$
                                                newTable);
                                        if (connection instanceof DatabaseConnection) {
                                            String dbType = ((DatabaseConnection) connection).getDatabaseType();
                                            String schema = ((DatabaseConnection) connection).getSchema();
                                            tableIdAndDbTypeMap.put(newTable.getId(), dbType);
                                            if (schema != null && !schema.equals("")) { //$NON-NLS-1$
                                                tableIdAndDbSchemaMap.put(newTable.getId(), schema);
                                            }
                                        }
                                    }
                                }
                            }
                            if (connection instanceof DatabaseConnection) {
                                DatabaseConnection dbConnection = (DatabaseConnection) connection;
                                QueriesConnection queriesConnection = dbConnection.getQueries();
                                if (queriesConnection != null) {
                                    List<Query> qs = queriesConnection.getQuery();
                                    for (Query query : qs) {
                                        repositoryQueryStoreMap.put(connectionItem.getProperty().getId() + " - " //$NON-NLS-1$
                                                + query.getLabel(), query);
                                    }
                                }
                            }

                            monitorWrap.worked(1);
                        }

                    }
                }

                monitorWrap.done();
            }
        };

        try {
            progressDialog.executeProcess();
        } catch (InvocationTargetException e) {
            ExceptionHandler.process(e);
            return;
        } catch (Exception e) {
            ExceptionHandler.process(e);
            return;
        }
    }

    /**
     * ftang Comment method "getElement".
     * 
     * @return an instance of Element
     */
    public Element getElement() {
        return elem;
    }

    /**
     * 
     */
    private boolean checkErrorsWhenViewRefreshed;

    public void addComponents(boolean forceRedraw) {
        addComponents(forceRedraw, true, 0);
    }

    /**
     * yzhang Comment method "addcomponents".
     * 
     * @param forceRedraw
     * @param reInitialize
     */
    public void addComponents(boolean forceRedraw, boolean reInitialize) {
        addComponents(forceRedraw, reInitialize, 0);
    }

    protected void disposeChildren() {
        if (composite != null && !composite.isDisposed()) {
            // Empty the composite before use (kinda refresh) :
            Control[] ct = composite.getChildren();
            for (int i = 0; i < ct.length; i++) {
                ct[i].dispose();
            }
        }
    }

    private static final int DEFAULT_GROUP_HEIGHT = 20;

    /**
     * Initialize all components for the defined section for this node.
     */
    public synchronized void addComponents(boolean forceRedraw, boolean reInitialize, int height) {
        // achen modifed to fix feature 0005991 if composite.isDisposed return
        if (elem == null || composite.isDisposed()) {
            return;
        }

        checkErrorsWhenViewRefreshed = true;
        int heightSize = 0, maxRowSize = 0, nbInRow, numInRow;
        int maxRow;
        boolean isCompute = false;

        if (!forceRedraw) {
            boolean needRedraw = isNeedRedraw();
            if (!needRedraw) {
                // System.out.println("no need redraw");
                return;
            }
        }

        Control lastControl = null;
        if (reInitialize) {
            if (currentComponent != null) {
                disposeChildren();
            }
        } else {
            heightSize = height;
        }

        hashCurControls = new DualHashBidiMap();

        maxRow = 0;
        List<? extends IElementParameter> listParam = elem.getElementParametersWithChildrens();
        Map<String, Integer> groupPosition = new HashMap<String, Integer>();
        for (int i = 0; i < listParam.size(); i++) {
            if (listParam.get(i).getCategory() == section) {
                if (listParam.get(i).getNumRow() > maxRow && listParam.get(i).isShow(listParam)) {
                    maxRow = listParam.get(i).getNumRow();
                }
            }
        }

        IElementParameter synchronizeSchemaParam = elem.getElementParameter(EParameterName.NOT_SYNCHRONIZED_SCHEMA.getName());

        if (synchronizeSchemaParam != null) {
            // if the node don't contains a schema type and accept an input flow and is not synchronized
            // display a schema on the first line just the type to synchronize the schema
            synchronizeSchemaParam.setShow(!((Node) elem).isSchemaSynchronized());
        }

        generator.initController(this);

        // System.out.println("********************** NEW ADDCOMPONENTS
        // **********************");
        // TabbedPropertyComposite tabbedPropertyComposite = this.getTabbedPropertyComposite();
        int additionalHeightSize = 0;
        boolean hasDynamicRow = false;
        for (int i = 0; i < listParam.size(); i++) {
            IElementParameter curParam = listParam.get(i);
            if (curParam.getCategory() == section) {
                if (curParam.getField() != EParameterFieldType.TECHNICAL) {
                    if (curParam.isShow(listParam)) {
                        AbstractElementPropertySectionController controller = generator.getController(curParam.getField(), this);

                        if (controller == null) {
                            continue;
                        }
                        if (controller.hasDynamicRowSize()) {
                            hasDynamicRow = true;
                            break;
                        }
                    }
                }
            }
        }
        if (hasDynamicRow) {
            additionalHeightSize = estimatePropertyHeightSize(maxRow, listParam);
        }

        //long lastTime = TimeMeasure.timeSinceBegin("DC:refresh:" + getCurrentComponent()); //$NON-NLS-1$
        for (int curRow = 1; curRow <= maxRow; curRow++) {
            maxRowSize = 0;
            nbInRow = 0;
            for (int i = 0; i < listParam.size(); i++) {
                IElementParameter curParam = listParam.get(i);
                if (curParam.getCategory() == section) {
                    if (curParam.getNumRow() == curRow && curParam.isShow(listParam)
                            && (curParam.getField() != EParameterFieldType.TECHNICAL)) {
                        nbInRow++;
                    }
                }
            }
            numInRow = 0;
            lastControl = null;
            curRowSize = 0;
            for (int i = 0; i < listParam.size(); i++) {
                IElementParameter curParam = listParam.get(i);
                if (curParam.getCategory() == section) {
                    if (curParam.getNumRow() == curRow && (curParam.getField() != EParameterFieldType.TECHNICAL)) {
                        // System.out.println("test:" + curParam.getName() + "
                        // field:"+curParam.getField());
                        if (curParam.isShow(listParam)) {
                            // System.out.println("show:" + curParam.getName()+
                            // " field:"+curParam.getField());
                            numInRow++;
                            AbstractElementPropertySectionController controller = generator.getController(curParam.getField(),
                                    this);

                            if (controller == null) {
                                continue;
                            }
                            if (controller.hasDynamicRowSize()) {
                                controller.setAdditionalHeightSize(additionalHeightSize);
                            }

                            String groupName = curParam.getGroup();
                            Composite subComposite = null;

                            if (groupName != null) {
                                if (!hashCurControls.containsKey(groupName)) {
                                    if (groupPosition.size() > 0) {
                                        heightSize += DEFAULT_GROUP_HEIGHT;
                                    }
                                    new GroupController(this).createControl(composite, curParam, numInRow, nbInRow, heightSize,
                                            lastControl);
                                    groupPosition.put(groupName, heightSize);
                                }
                                subComposite = (Composite) hashCurControls.get(groupName);
                                int h2 = heightSize - groupPosition.get(groupName);
                                lastControl = controller
                                        .createControl(subComposite, curParam, numInRow, nbInRow, h2, lastControl);

                            } else {
                                if (isCompactView()) {
                                    int h3 = DEFAULT_GROUP_HEIGHT * (groupPosition.size() > 0 ? 1 : 0) + heightSize;
                                    lastControl = controller.createControl(composite, curParam, numInRow, nbInRow, h3,
                                            lastControl);
                                } else {
                                    if (numInRow > 1 && nbInRow > 1) {
                                        heightSize += maxRowSize;
                                    }
                                    int h3 = DEFAULT_GROUP_HEIGHT * (groupPosition.size() > 0 ? 1 : 0) + heightSize;
                                    lastControl = controller.createControl(composite, curParam, 1, 1, h3, null);
                                }
                            }

                            //                            lastTime = TimeMeasure.timeSinceBegin("DC:refresh:" + getCurrentComponent()) - lastTime; //$NON-NLS-1$
                            // if (DynamicTabbedPropertySection.DEBUG_TIME) {
                            //                                System.out.println("DC;create:" + curParam.getField().getName() + ";" + getCurrentComponent() //$NON-NLS-1$ //$NON-NLS-2$
                            //                                        + ";" + lastTime); //$NON-NLS-1$
                            // }

                            // System.out.println("param:" + curParam.getName()
                            // + " - curRowSize:" + curRowSize);

                            maxRowSize = 0;
                            if (curRowSize > maxRowSize) {
                                maxRowSize = curRowSize;
                                isCompute = true;
                            }
                        }
                    }
                }
            }
            if (isCompute) {
                heightSize += maxRowSize;
                isCompute = false;
            }

        }
        if (synchronizeSchemaParam != null) {
            synchronizeSchemaParam.setShow(false);
        }

        resizeScrolledComposite();
    }

    // /**
    // * DOC Administrator Comment method "updateMainParameters".
    // */
    // protected void updateMainParameters() {
    // oldQueryStoreType = (String) elem.getPropertyValue(EParameterName.QUERYSTORE_TYPE.getName());
    // if (oldQueryStoreType != null) {
    // if (oldQueryStoreType.equals(EmfComponent.REPOSITORY)) {
    // showQueryStoreRepositoryList(true);
    // updateRepositoryList();
    // } else {
    // showQueryStoreRepositoryList(false);
    // }
    // }
    //
    // IElementParameter param = elem.getElementParameter(EParameterName.PROPERTY_TYPE.getName());
    // if (param != null) {
    // oldPropertyType = (String) param.getValue();
    // if (param.isShow(elem.getElementParameters())) {
    // if (oldPropertyType.equals(EmfComponent.REPOSITORY)) {
    // showPropertyRepositoryList(true, false);
    // updateRepositoryList();
    // } else {
    // showPropertyRepositoryList(false, false);
    // }
    // } else {
    // showPropertyRepositoryList(false, false);
    // }
    // }
    // // for job settings extra (feature 2710)
    // param = elem.getElementParameter(extraPropertyTypeName);
    // if (param != null) {
    // oldPropertyType = (String) param.getValue();
    // if (param.isShow(elem.getElementParameters())) {
    // if (oldPropertyType.equals(EmfComponent.REPOSITORY)) {
    // showPropertyRepositoryList(true, true);
    // updateRepositoryList();
    // } else {
    // showPropertyRepositoryList(false, true);
    // }
    // } else {
    // showPropertyRepositoryList(false, true);
    // }
    // }
    // oldProcessType = (String) elem.getPropertyValue(EParameterName.PROCESS_TYPE_PROCESS.getName());
    // if (oldProcessType != null) {
    // String[] list =
    // elem.getElementParameter(EParameterName.PROCESS_TYPE_PROCESS.getName()).getListItemsDisplayName();
    // if ((oldProcessType.equals("NO_PROCESS") || (list.length == 0))) { //$NON-NLS-1$
    // updateProcessList();
    // updateContextList();
    // if (elem instanceof Node) {
    // ((Node) elem).checkAndRefreshNode();
    // }
    // }
    // }
    // }

    /**
     * DOC Administrator Comment method "isNeedRedraw".
     * 
     * @return
     */
    protected boolean isNeedRedraw() {
        boolean needRedraw = false;
        for (IElementParameter elementParameter : elem.getElementParametersWithChildrens()) {
            if (elementParameter.getCategory().equals(section)
                    && (elementParameter.getField() != EParameterFieldType.SCHEMA_TYPE)
                    && (elementParameter.getField() != EParameterFieldType.QUERYSTORE_TYPE)) {
                // if the component must be displayed, then check if the
                // control exists or not.
                boolean show = elementParameter.isShow(elem.getElementParameters());
                Object control;
                if (elementParameter.getParentParameter() == null) {
                    control = hashCurControls.get(elementParameter.getName());
                } else {
                    control = hashCurControls.get(elementParameter.getParentParameter().getName() + ":" //$NON-NLS-1$
                            + elementParameter.getName());
                }
                if ((control == null && show) || (control != null && !show)) {
                    needRedraw = true;
                    break;

                }
            }
        }
        return needRedraw;
    }

    /**
     * DOC nrousseau Comment method "estimatePropertyHeightSize".
     * 
     * @param maxRow
     * @param listParam
     * @param tabbedPropertyComposite
     */
    private int estimatePropertyHeightSize(int maxRow, List<? extends IElementParameter> listParam) {
        int estimatedHeightSize = 0, estimatedMaxRowSize = 0;
        int additionalHeightSize = 0;
        int compositeHeight = getParent().getBounds().height;

        // System.out.println("size composite:" + compositeHeight);

        int nbDynamic = 0;
        for (int curRow = 1; curRow <= maxRow; curRow++) {
            estimatedMaxRowSize = 0;
            for (int i = 0; i < listParam.size(); i++) {
                IElementParameter curParam = listParam.get(i);
                if (curParam.getCategory() == section) {
                    if (curParam.getNumRow() == curRow && (curParam.getField() != EParameterFieldType.TECHNICAL)) {
                        // System.out.println("test:" + curParam.getName() + "
                        // field:"+curParam.getField());
                        if (curParam.isShow(listParam)) {
                            // System.out.println("show:" + curParam.getName()+
                            // " field:"+curParam.getField());
                            AbstractElementPropertySectionController controller = generator.getController(curParam.getField(),
                                    this);

                            if (controller == null) {
                                break;
                            }
                            int estimatedSize = controller.estimateRowSize(composite, curParam);
                            if (controller.hasDynamicRowSize()) {
                                nbDynamic++;
                            }
                            // System.out.println("param:" + curParam.getName()
                            // + " - estimatedSize:" + estimatedSize);

                            if (estimatedSize > estimatedMaxRowSize) {
                                estimatedMaxRowSize = estimatedSize;
                            }
                        }
                    }
                }
            }
            estimatedHeightSize += estimatedMaxRowSize;
        }
        // System.out.println("*** ESTIMATED SIZE:" + estimatedHeightSize + "
        // ***");
        int emptySpace = compositeHeight - estimatedHeightSize;
        // System.out.println("--- EMPTY SPACE:" + emptySpace);
        if (emptySpace > 0 && nbDynamic > 0) {
            additionalHeightSize = emptySpace / nbDynamic;
            // System.out.println("--- DIVIDED ADDITIONAL HEIGHT (for each
            // dynamic):" + additionalHeightSize);
        }
        return additionalHeightSize;
    }

    private void resizeScrolledComposite() {
        // Point compositeSize = composite.computeSize(SWT.DEFAULT, SWT.DEFAULT);
        //
        // Point parentSize = getParent().getSize();
        //
        // System.out.println("compositeSize:" + compositeSize + " / parentSize:" + parentSize);

        lastCompositeSize = getParent().getClientArea().height;

        // setMinSize(compositeSize);
        propertyResized = true;
    }

    public void refresh() {

        Thread thread = new Thread() {

            @Override
            public void run() {
                try {
                    operationInThread();
                } catch (Exception e) {
                    // e.printStackTrace();
                    ExceptionHandler.process(e);
                }
            }
        };
        thread.start();
    }

    private void operationInThread() {
        // TimeMeasure.display = false;
        // TimeMeasure.measureActive = true;
        //        TimeMeasure.begin("DC:refresh:" + getCurrentComponent()); //$NON-NLS-1$

        if (elem == null) {
            return;
        }
        List<? extends IElementParameter> listParam = elem.getElementParameters();

        // IElementParameter jobParam = elem.getElementParameterFromField(EParameterFieldType.PROCESS_TYPE);
        // if (jobParam != null) {
        // updateContextList(jobParam);
        // if (elem instanceof Node) {
        // ((Node) elem).checkAndRefreshNode();
        // }
        // }

        Boolean updateNeeded = (Boolean) elem.getPropertyValue(updataComponentParamName);

        if (updateNeeded != null) {
            if (updateNeeded) {
                Display.getDefault().syncExec(new Runnable() {

                    public void run() {
                        if (elem != null) {
                            addComponents(forceRedraw);
                            elem.setPropertyValue(updataComponentParamName, new Boolean(false));
                            forceRedraw = false;
                        }
                    }
                });
            }
        }

        for (int i = 0; i < listParam.size(); i++) {
            if (listParam.get(i).getCategory() == section) {
                if (listParam.get(i).isShow(listParam)) {

                    final IElementParameter e = listParam.get(i);

                    Display.getDefault().syncExec(new Runnable() {

                        public void run() {
                            if (generator != null) {
                                AbstractElementPropertySectionController controller = generator.getController(e.getField(),
                                        MultipleThreadDynamicComposite.this);
                                if (controller != null) {
                                    controller.refresh(e, checkErrorsWhenViewRefreshed);
                                }
                            }

                        }
                    });

                }
            }
        }
        if (propertyResized) {
            Display.getDefault().syncExec(new Runnable() {

                public void run() {
                    try {
                        removeListener(SWT.Resize, resizeListener);
                        getParent().layout();

                        composite.pack();
                        propertyResized = false;
                        addListener(SWT.Resize, resizeListener);
                    } catch (Exception e) {
                    }
                }
            });

        }

        checkErrorsWhenViewRefreshed = false;
        //        long time = TimeMeasure.timeSinceBegin("DC:refresh:" + getCurrentComponent()); //$NON-NLS-1$
        //        TimeMeasure.end("DC:refresh:" + getCurrentComponent()); //$NON-NLS-1$
        // if (DynamicTabbedPropertySection.DEBUG_TIME) {
        //            System.out.println("DC;total;" + getCurrentComponent() + ";" + time); //$NON-NLS-1$ //$NON-NLS-2$
        // }

        isRefreshing = false;
    }

    public static boolean isRefreshing = false;

    private final Listener resizeListener = new Listener() {

        public void handleEvent(Event event) {
            resizeLimiter.resetTimer();
            resizeLimiter.startIfExecutable(true, null);
        }
    };

    private final ExecutionLimiter resizeLimiter = new ExecutionLimiter(250, true) {

        @Override
        public void execute(final boolean isFinalExecution, Object data) {
            if (!isDisposed()) {
                getDisplay().asyncExec(new Runnable() {

                    public void run() {
                        if (!isDisposed() && !getParent().isDisposed()) {
                            int currentSize = getParent().getClientArea().height;
                            if (getLastCompositeSize() != currentSize) {
                                addComponents(true);
                                refresh();
                            }
                        }
                    }

                });
            }
        }
    };

    /**
     * Getter for isCompactView.
     * 
     * @return the isCompactView
     */
    public boolean isCompactView() {
        return this.isCompactView;
    }

    /**
     * Sets the isCompactView.
     * 
     * @param isCompactView the isCompactView to set
     */
    public void setCompactView(boolean isCompactView) {
        this.isCompactView = isCompactView;
    }

    /**
     * Set the section of the tabbed property.
     * 
     * @param section
     */
    public MultipleThreadDynamicComposite(Composite parentComposite, int styles, final EComponentCategory section,
            Element element, boolean isCompactView) {
        super(parentComposite, styles);
        setCompactView(isCompactView);
        // for job settings extra (feature 2710)
        // if (section == EComponentCategory.EXTRA) {
        // updataComponentParamName =
        // JobSettingsConstants.getExtraParameterName(EParameterName.UPDATE_COMPONENTS.getName());
        // } else {
        updataComponentParamName = EParameterName.UPDATE_COMPONENTS.getName();
        // }
        FormData d = new FormData();
        d.left = new FormAttachment(0, 0);
        d.right = new FormAttachment(100, 0);
        d.top = new FormAttachment(0, 0);
        d.bottom = new FormAttachment(100, 0);
        setLayoutData(d);

        setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));

        setExpandHorizontal(true);
        // setExpandVertical(true);

        composite = new WidgetFactory().createComposite(this, SWT.NO_FOCUS);
        setContent(composite);

        generator = new DynamicPropertyGenerator();
        this.section = section;
        this.elem = element;
        if (elem instanceof Node) {
            process = (Process) ((Node) elem).getProcess();
        } else if (elem instanceof SubjobContainer) {
            process = (Process) ((SubjobContainer) elem).getProcess();
        }
        if (elem instanceof org.talend.designer.core.ui.editor.connections.Connection) {
            org.talend.designer.core.ui.editor.connections.Connection connection;
            connection = (org.talend.designer.core.ui.editor.connections.Connection) elem;
            process = (Process) connection.getSource().getProcess();
        }
        if (elem instanceof Process) {
            process = (Process) elem;
        }
        // added by achen fix 0005991 & 0005993
        if (elem instanceof StatsAndLogsElement || elem instanceof ImplicitContextLoadElement) {
            process = ActiveProcessTracker.getCurrentProcess();
        }
        // end
        if (process != null) {
            part = process.getEditor();
        }
        FormLayout layout = new FormLayout();
        layout.marginWidth = ITabbedPropertyConstants.HSPACE + 2;
        layout.marginHeight = ITabbedPropertyConstants.VSPACE;
        layout.spacing = ITabbedPropertyConstants.VMARGIN + 1;
        composite.setLayout(layout);

        repositoryQueryStoreMap = new HashMap<String, Query>();
        repositoryConnectionItemMap = new HashMap<String, ConnectionItem>();
        repositoryTableMap = new HashMap<String, IMetadataTable>();
        hashCurControls = new DualHashBidiMap();

        if ((currentComponent == null) || (!currentComponent.equals(elem.getElementName()))) {
            forceRedraw = true;
            elem.setPropertyValue(updataComponentParamName, Boolean.TRUE);

        }
        currentComponent = elem.getElementName();

        propertyResized = true;
        addListener(SWT.Resize, resizeListener);
        addListener(SWT.FocusOut, new Listener() {

            public void handleEvent(Event event) {
                // if the focus is lost reinitialise all information from repository
                repositoryTableMap.clear();
                repositoryQueryStoreMap.clear();
                repositoryConnectionItemMap.clear();
            }

        });

        if (getCommandStack() != null) {
            getCommandStack().addCommandStackEventListener(commandStackEventListener);
        }
        // for job settings extra (feature 2710)
        // extraPropertyTypeName = JobSettingsConstants.getExtraParameterName(EParameterName.PROPERTY_TYPE.getName());
        // extraRepositoryPropertyTypeName =
        // JobSettingsConstants.getExtraParameterName(EParameterName.REPOSITORY_PROPERTY_TYPE
        // .getName());
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.swt.widgets.Widget#dispose()
     */
    @Override
    public void dispose() {
        CommandStack cmdStack = getCommandStack();
        if (cmdStack != null) {
            cmdStack.removeCommandStackEventListener(commandStackEventListener);
        }
        super.dispose();
        process = null;
        elem = null;
        part = null;
        generator.dispose();
        generator = null;
        hashCurControls.clear();
    }

    CommandStackEventListener commandStackEventListener = new CommandStackEventListener() {

        public void stackChanged(CommandStackEvent event) {
            int detail = event.getDetail();
            if ((getElement() instanceof org.talend.designer.core.ui.editor.connections.Connection)
                    && (event.getCommand() instanceof ChangeMetadataCommand)
                    && (0 != (detail & CommandStack.POST_EXECUTE) || 0 != (detail & CommandStack.POST_REDO) // 
                    || 0 != (detail & CommandStack.POST_REDO))) {
                addComponents(true);
                refresh();
            }
            if (0 != (detail & CommandStack.POST_EXECUTE) || 0 != (detail & CommandStack.POST_UNDO)
                    || 0 != (detail & CommandStack.POST_REDO)) {
                Boolean updateNeeded = (Boolean) elem.getPropertyValue(updataComponentParamName);
                if (updateNeeded) {
                    refresh();
                }
            }
        }
    };

    /**
     * yzhang Comment method "setCurRowSize" Sets the curRowSize.
     * 
     * @param curRowSize int
     */
    public void setCurRowSize(int curRowSize) {
        this.curRowSize = curRowSize;
    }

    /**
     * Getter for currentComponent.
     * 
     * @return the currentComponent
     */
    public String getCurrentComponent() {
        return this.currentComponent;
    }

    /**
     * Getter for curRowSize.
     * 
     * @return the curRowSize
     */
    public int getCurRowSize() {
        return this.curRowSize;
    }

    /**
     * Getter for hashCurControls.
     * 
     * @return the hashCurControls
     */
    public BidiMap getHashCurControls() {
        return this.hashCurControls;
    }

    /**
     * Getter for part.
     * 
     * @return the part
     */
    public AbstractMultiPageTalendEditor getPart() {
        return this.part;
    }

    /**
     * Getter for section.
     * 
     * @return the section
     */
    public EComponentCategory getSection() {
        return this.section;
    }

    /**
     * Get the command stack of the Gef editor.
     * 
     * @return
     */
    protected CommandStack getCommandStack() {
        if (part != null && part.getTalendEditor() != null) {
            Object adapter = part.getTalendEditor().getAdapter(CommandStack.class);
            return (CommandStack) adapter;
        } else {
            return null;
        }
    }

    /**
     * qzhang Comment method "getDefaultRepository".
     * 
     * @return
     */
    private String getDefaultRepository(IElementParameter baseParam, boolean istable, String defaultPropertyValue) {
        boolean metadataInput = false;
        if (((Node) elem).getCurrentActiveLinksNbInput(EConnectionType.FLOW_MAIN) > 0
                || ((Node) elem).getCurrentActiveLinksNbInput(EConnectionType.FLOW_REF) > 0
                || ((Node) elem).getCurrentActiveLinksNbInput(EConnectionType.TABLE) > 0) {
            metadataInput = true;
        }

        if (metadataInput && istable) {
            return (String) baseParam.getChildParameters().get(EParameterName.REPOSITORY_SCHEMA_TYPE.getName()).getValue();
        }
        Object propertyValue = elem.getPropertyValue(EParameterName.REPOSITORY_PROPERTY_TYPE.getName());
        if ((propertyValue == null || !(propertyValue instanceof String)) && defaultPropertyValue != null) {
            propertyValue = defaultPropertyValue;
        }
        if (propertyValue == null || propertyValue.equals("")) { //$NON-NLS-1$
            return ""; //$NON-NLS-1$
        }
        if (istable) {
            //
        } else {
            //
        }
        return ""; //$NON-NLS-1$
    }

    /**
     * Getter for tableIdAndDbTypeMap.
     * 
     * @return the tableIdAndDbTypeMap
     */
    public Map<String, String> getTableIdAndDbTypeMap() {
        if (this.tableIdAndDbTypeMap.isEmpty()) {
            updateRepositoryList();
        }
        return this.tableIdAndDbTypeMap;
    }

    /**
     * Getter for tableIdAndDbSchemaMap.
     * 
     * @return the tableIdAndDbSchemaMap
     */
    public Map<String, String> getTableIdAndDbSchemaMap() {
        if (this.tableIdAndDbSchemaMap.isEmpty()) {
            updateRepositoryList();
        }
        return this.tableIdAndDbSchemaMap;
    }

    /**
     * Getter for repositoryQueryStoreMap.
     * 
     * @return the repositoryQueryStoreMap
     */
    public Map<String, Query> getRepositoryQueryStoreMap() {
        if (this.repositoryQueryStoreMap.isEmpty()) {
            updateRepositoryList();
        }
        return repositoryQueryStoreMap;
    }

    /**
     * dev Comment method "getRepositoryTableMap".
     * 
     * @return Map
     */
    public Map<String, IMetadataTable> getRepositoryTableMap() {
        if (this.repositoryTableMap.isEmpty()) {
            updateRepositoryList();
        }
        return this.repositoryTableMap;
    }

    /**
     * dev Comment method "getRepositoryConnectionItemMap".
     * 
     * @return Map
     */
    public Map<String, ConnectionItem> getRepositoryConnectionItemMap() {
        if (this.repositoryConnectionItemMap.isEmpty()) {
            updateRepositoryList();
        }
        return this.repositoryConnectionItemMap;
    }

    /**
     * Getter for lastCompositeSize.
     * 
     * @return the lastCompositeSize
     */
    public int getLastCompositeSize() {
        return this.lastCompositeSize;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.talend.designer.core.ui.editor.properties.controllers.generator.IDynamicProperty#getComposite()
     */
    public Composite getComposite() {
        return composite;
    }

}
