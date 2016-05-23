// ============================================================================
//
// Copyright (C) 2006-2016 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.designer.core.generic.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.avro.Schema;
import org.apache.commons.lang.StringUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.talend.commons.exception.BusinessException;
import org.talend.commons.exception.ExceptionHandler;
import org.talend.commons.runtime.model.components.IComponentConstants;
import org.talend.components.api.component.ComponentDefinition;
import org.talend.components.api.component.Connector;
import org.talend.components.api.component.PropertyPathConnector;
import org.talend.components.api.component.Trigger;
import org.talend.components.api.properties.ComponentProperties;
import org.talend.components.api.service.ComponentService;
import org.talend.core.model.components.IComponent;
import org.talend.core.model.components.IComponentsFactory;
import org.talend.core.model.metadata.types.JavaType;
import org.talend.core.model.metadata.types.JavaTypesManager;
import org.talend.core.model.process.EComponentCategory;
import org.talend.core.model.process.EConnectionType;
import org.talend.core.model.process.EParameterFieldType;
import org.talend.core.model.process.IElement;
import org.talend.core.model.process.IElementParameterDefaultValue;
import org.talend.core.model.process.INode;
import org.talend.core.model.process.INodeConnector;
import org.talend.core.model.utils.ContextParameterUtils;
import org.talend.core.ui.component.ComponentsFactoryProvider;
import org.talend.core.utils.TalendQuoteUtils;
import org.talend.daikon.NamedThing;
import org.talend.daikon.properties.PresentationItem;
import org.talend.daikon.properties.Properties;
import org.talend.daikon.properties.Properties.Deserialized;
import org.talend.daikon.properties.Property;
import org.talend.daikon.properties.SchemaProperty;
import org.talend.daikon.properties.presentation.Form;
import org.talend.daikon.properties.presentation.Widget;
import org.talend.designer.core.generic.constants.IGenericConstants;
import org.talend.designer.core.generic.i18n.Messages;
import org.talend.designer.core.generic.model.Component;
import org.talend.designer.core.generic.model.GenericElementParameter;
import org.talend.designer.core.generic.model.GenericNodeConnector;
import org.talend.designer.core.generic.model.GenericTableUtils;
import org.talend.designer.core.generic.model.mapping.WidgetFieldTypeMapper;
import org.talend.designer.core.model.FakeElement;
import org.talend.designer.core.model.components.ElementParameter;
import org.talend.designer.core.model.components.ElementParameterDefaultValue;
import org.talend.designer.core.model.components.NodeConnector;

/**
 * created by hcyi on Sep 11, 2015 Detailled comment
 *
 */
public class ComponentsUtils {

    private static List<IComponent> components = null;

    public static ComponentService getComponentService() {
        ComponentService compService = null;
        BundleContext bundleContext = FrameworkUtil.getBundle(ComponentsUtils.class).getBundleContext();
        ServiceReference<ComponentService> compServiceRef = bundleContext.getServiceReference(ComponentService.class);
        if (compServiceRef != null) {
            compService = bundleContext.getService(compServiceRef);
        }
        return compService;
    }

    public static ComponentProperties getComponentProperties(String compName) {
        return getComponentService().getComponentProperties(compName);
    }

    public static List<ComponentDefinition> getPossibleComponents(ComponentProperties properties) {
        List<ComponentDefinition> possibleComponents = new ArrayList<ComponentDefinition>();
        try {
            possibleComponents.addAll(getComponentService().getPossibleComponents(properties));
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return possibleComponents;
    }

    public static void loadComponents(ComponentService service) {
        IComponentsFactory componentsFactory = null;
        if (componentsFactory == null) {
            componentsFactory = ComponentsFactoryProvider.getInstance();
        }
        Set<IComponent> componentsList = componentsFactory.getComponents();
        if (components == null) {
            components = new ArrayList<IComponent>();
        } else {
            componentsList.removeAll(components);
        }
        Map<String, IComponent> existComponents = new HashMap<String, IComponent>();

        for (IComponent component : componentsList) {
            existComponents.put(component.getName(), component);
        }

        // Load components from service
        Set<ComponentDefinition> componentDefinitions = service.getAllComponents();
        for (ComponentDefinition componentDefinition : componentDefinitions) {
            try {
                Component currentComponent = new Component(componentDefinition);
                componentsList.add(currentComponent);
            } catch (BusinessException e) {
                ExceptionHandler.process(e);
            }
        }
    }

    public static List<ElementParameter> getParametersFromForm(IElement element, Form form) {
        return getParametersFromForm(element, null, (ComponentProperties) form.getProperties(), form);
    }

    public static List<ElementParameter> getParametersFromForm(IElement element, EComponentCategory category,
            ComponentProperties compProperties, Form form) {
        return getParametersFromForm(element, category, compProperties, compProperties, null, form, null, null);
    }

    /**
     * DOC ycbai Comment method "loadParametersFromForm".
     * <p>
     * Get element parameters of <code>element</code> from <code>form</code>.
     * 
     * @param node optional, used if there is a component setting up the properties
     * @param element
     * @param category
     * @param form
     * @return parameters list
     */
    private static List<ElementParameter> getParametersFromForm(IElement element, EComponentCategory category,
            ComponentProperties rootProperty, ComponentProperties compProperties, String parentPropertiesPath, Form form,
            Widget parentWidget, AtomicInteger lastRowNum) {
        List<ElementParameter> elementParameters = new ArrayList<>();
        List<String> parameterNames = new ArrayList<>();
        EComponentCategory compCategory = category;
        if (compCategory == null) {
            compCategory = EComponentCategory.BASIC;
        }
        AtomicInteger lastRN = lastRowNum;
        if (lastRN == null) {
            lastRN = new AtomicInteger();
        }
        if (form == null) {
            return elementParameters;
        }
        ComponentProperties componentProperties = compProperties;
        if (componentProperties == null) {
            componentProperties = (ComponentProperties) form.getProperties();
        }
        if (element instanceof INode) {
            INode node = (INode) element;
            // FIXME - this should be able to be removed TUP-4053
            // Set the properties only one time to get the top-level properties object
            if (node.getComponentProperties() == null) {
                node.setComponentProperties(componentProperties);
            }
        }

        // Have to initialize for the messages
        Collection<Widget> formWidgets = form.getWidgets();
        for (Widget widget : formWidgets) {
            NamedThing widgetProperty = widget.getContent();

            String propertiesPath = getPropertiesPath(parentPropertiesPath, null);
            if (widgetProperty instanceof Form) {
                Form subForm = (Form) widgetProperty;
                ComponentProperties subProperties = (ComponentProperties) subForm.getProperties();
                // Reset properties path
                if (!isSameComponentProperties(componentProperties, widgetProperty)) {
                    propertiesPath = getPropertiesPath(parentPropertiesPath, subProperties.getName());
                }
                elementParameters.addAll(getParametersFromForm(element, compCategory, rootProperty, subProperties,
                        propertiesPath, subForm, widget, lastRN));
                continue;
            }

            GenericElementParameter param = new GenericElementParameter(element, rootProperty, form, widget,
                    getComponentService());
            String parameterName = propertiesPath.concat(param.getName());
            param.setName(parameterName);
            param.setCategory(compCategory);
            param.setShow(parentWidget == null ? !widget.isHidden() : !parentWidget.isHidden() && !widget.isHidden());
            int rowNum = 0;
            if (widget.getOrder() != 1) {
                rowNum = lastRN.get();
            } else {
                rowNum = widget.getRow();
                if (parentWidget != null) {
                    rowNum += parentWidget.getRow();
                }
                rowNum = rowNum + lastRN.get();
            }
            param.setNumRow(rowNum);
            lastRN.set(rowNum);
            // handle form...

            EParameterFieldType fieldType = getFieldType(widget, widgetProperty);
            // rootProperty.getAvailableConnectors(null, true)
            param.setFieldType(fieldType != null ? fieldType : EParameterFieldType.TEXT);
            if (widgetProperty instanceof SchemaProperty) {
                boolean found = false;
                // set a default connector
                param.setContext(EConnectionType.FLOW_MAIN.getName());
                for (Connector connector : rootProperty.getAvailableConnectors(null, true)) {
                    if (!(((SchemaProperty) widgetProperty).getValue() instanceof Schema)) {
                        continue;
                    }
                    if (connector instanceof PropertyPathConnector) {
                        String linkedSchema = ((PropertyPathConnector) connector).getPropertyPath() + ".schema"; //$NON-NLS-1$
                        if (parameterName.equals(linkedSchema)) {
                            found = true;
                            param.setContext(connector.getName());
                            IElementParameterDefaultValue defaultValue = new ElementParameterDefaultValue();
                            Schema schema = (Schema) ((SchemaProperty) widgetProperty).getValue();
                            defaultValue.setDefaultValue(new Schema.Parser().parse(schema.toString()));
                            param.getDefaultValues().add(defaultValue);
                        }
                    }
                }
                if (!found) {
                    // check in the input schema
                    // for now we only handle input schema named MAIN. But we will name them "FLOW" to keep
                    // compatibility.
                    for (Connector connector : rootProperty.getAvailableConnectors(null, false)) {
                        if (!(((SchemaProperty) widgetProperty).getValue() instanceof Schema)) {
                            continue;
                        }
                        if (connector instanceof PropertyPathConnector) {
                            String linkedSchema = ((PropertyPathConnector) connector).getPropertyPath() + ".schema"; //$NON-NLS-1$
                            if (parameterName.equals(linkedSchema)) {
                                if (GenericNodeConnector.INPUT_CONNECTOR.equals(connector.getName())) {
                                    param.setContext(EConnectionType.FLOW_MAIN.getName());
                                } else {
                                    param.setContext(connector.getName());
                                }
                                IElementParameterDefaultValue defaultValue = new ElementParameterDefaultValue();
                                Schema schema = (Schema) ((SchemaProperty) widgetProperty).getValue();
                                defaultValue.setDefaultValue(new Schema.Parser().parse(schema.toString()));
                                param.getDefaultValues().add(defaultValue);
                            }
                        }
                    }
                }
            }
            if (widgetProperty instanceof PresentationItem) {
                param.setValue(widgetProperty.getDisplayName());
            } else if (widgetProperty instanceof Property) {
                Property property = (Property) widgetProperty;
                param.setRequired(property.isRequired());
                param.setValue(getParameterValue(element, property));
                if (EParameterFieldType.NAME_SELECTION_AREA.equals(fieldType)) {
                    // Disable context support for this filed type.
                    param.setSupportContext(false);
                } else {
                    param.setSupportContext(isSupportContext(property));
                }
                property.setTaggedValue(IComponentConstants.SUPPORT_CONTEXT, param.isSupportContext());
                Object cmTV = property.getTaggedValue(IGenericConstants.IS_CONTEXT_MODE);
                param.setReadOnly(Boolean.valueOf(String.valueOf(cmTV)));
                boolean isDynamic = Boolean.valueOf(String.valueOf(property.getTaggedValue(IGenericConstants.IS_DYNAMIC)));
                param.setContextMode(isDynamic);
                List<?> values = property.getPossibleValues();
                if (values != null || EParameterFieldType.CLOSED_LIST.equals(fieldType)) {
                    if (values == null) {
                        values = Collections.emptyList();
                    }
                    param.setPossibleValues(values);
                    List<String> possVals = new ArrayList<>();
                    List<String> possValsDisplay = new ArrayList<>();
                    for (Object obj : values) {
                        if (obj instanceof NamedThing) {
                            NamedThing nal = (NamedThing) obj;
                            possVals.add(nal.getName());
                            possValsDisplay.add(nal.getDisplayName());
                        } else {
                            possVals.add(String.valueOf(obj));
                            possValsDisplay.add(String.valueOf(obj));
                        }
                    }
                    param.setListItemsDisplayName(possValsDisplay.toArray(new String[0]));
                    param.setListItemsDisplayCodeName(possValsDisplay.toArray(new String[0]));
                    param.setListItemsValue(possVals.toArray(new String[0]));
                }
            } else if (fieldType != null && fieldType.equals(EParameterFieldType.TABLE) && widgetProperty instanceof Properties) {
                Properties table = (Properties) widgetProperty;
                Form mainForm = table.getForm(Form.MAIN);
                param.setDisplayName(mainForm.getTitle());
                List<ElementParameter> parameters = getParametersFromForm(new FakeElement("table"), mainForm); //$NON-NLS-1$

                // table is always empty by default
                param.setSupportContext(false);

                List<String> codeNames = new ArrayList<>();
                List<String> possValsDisplay = new ArrayList<>();
                for (ElementParameter curParam : parameters) {
                    curParam.setFilter(null);
                    curParam.setContext(null);
                    curParam.setShowIf(null);
                    curParam.setNotShowIf(null);
                    curParam.setReadOnlyIf(null);
                    curParam.setNotReadOnlyIf(null);
                    curParam.setNoContextAssist(false);
                    curParam.setRaw(false);
                    curParam.setReadOnly(false);
                    codeNames.add(curParam.getName());
                    possValsDisplay.add(curParam.getDisplayName());
                }
                param.setListItemsDisplayName(possValsDisplay.toArray(new String[0]));
                param.setListItemsDisplayCodeName(codeNames.toArray(new String[0]));
                param.setListItemsValue(parameters.toArray(new ElementParameter[0]));
                String[] listItemsShowIf = new String[parameters.size()];
                String[] listItemsNotShowIf = new String[parameters.size()];
                param.setListItemsShowIf(listItemsShowIf);
                param.setListItemsNotShowIf(listItemsNotShowIf);
                param.setValue(GenericTableUtils.getTableValues(table, param));

            }
            // param.setReadOnly(false);
            param.setSerialized(true);
            param.setDynamicSettings(true);
            // Avoid adding duplicate prameter.
            if (!parameterNames.contains(parameterName)) {
                elementParameters.add(param);
                parameterNames.add(parameterName);
            }
        }
        return elementParameters;
    }

    /**
     * DOC nrousseau Comment method "getNameFromConnector".
     * 
     * @param connector
     * @return
     */
    public static String getNameFromConnector(Connector connector) {
        if (Connector.MAIN_NAME.equals(connector.getName())) {
            return EConnectionType.FLOW_MAIN.getName();
        } else {
            return connector.getName();
        }
    }

    /**
     * DOC ycbai Comment method "getRelatedParameters".
     * <p>
     * Get all element parameters related to the <code>parameter<code>.
     * For example the paramters from the form associated with PresentationItem type.
     * 
     * @param parameters
     * @return
     */
    public static List<ElementParameter> getRelatedParameters(GenericElementParameter parameter) {
        List<ElementParameter> params = new ArrayList<>();
        if (parameter == null) {
            return params;
        }
        Widget widget = parameter.getWidget();
        NamedThing content = widget.getContent();
        if (content instanceof PresentationItem) {
            PresentationItem pi = (PresentationItem) content;
            Form formtoShow = pi.getFormtoShow();
            List<ElementParameter> parametersFromForm = getParametersFromForm(parameter.getElement(), parameter.getCategory(),
                    parameter.getRootProperties(), formtoShow);
            params.addAll(parametersFromForm);
        }
        return params;
    }

    public static Object getParameterValue(IElement element, Property property) {
        Object paramValue = property.getValue();
        if (paramValue instanceof List) {
            return null;
        }
        Property.Type propertyType = property.getType();
        switch (propertyType) {
        case STRING:
            if (!(element instanceof FakeElement || ContextParameterUtils.isContainContextParam((String) paramValue))) {
                paramValue = TalendQuoteUtils.addQuotesIfNotExist((String) paramValue);
            }
            break;
        case ENUM:
            if (paramValue == null) {// TUP-4145
                List<?> possibleValues = property.getPossibleValues();
                if (possibleValues != null && possibleValues.size() > 0) {
                    paramValue = possibleValues.get(0);
                    property.setValue(paramValue);
                }
            }
            break;
        case BOOLEAN:
            if (paramValue == null) {
                paramValue = Boolean.FALSE;
                property.setValue(paramValue);
            }
            break;
        default:
            break;
        }
        return paramValue;
    }

    private static String getPropertiesPath(String parentPropertiesPath, String currentPropertiesName) {
        String propertiesPath = ""; //$NON-NLS-1$
        if (StringUtils.isNotBlank(currentPropertiesName)) {
            propertiesPath = propertiesPath.concat(currentPropertiesName).concat(IGenericConstants.EXP_SEPARATOR);
        }
        if (StringUtils.isNotBlank(parentPropertiesPath)) {
            propertiesPath = parentPropertiesPath.concat(propertiesPath);
        }
        return propertiesPath;
    }

    /**
     * DOC ycbai Comment method "getFieldType".
     * 
     * @param widget
     * @param widgetProperty
     * @param param
     * @param se
     * @return
     */
    private static EParameterFieldType getFieldType(Widget widget, NamedThing widgetProperty) {
        return WidgetFieldTypeMapper.getFieldType(widget, widgetProperty);
    }

    public static ComponentProperties getCurrentComponentProperties(ComponentProperties componentProperties, String paramName) {
        if (componentProperties == null || paramName == null) {
            return null;
        }
        String compPropertiesPath = getPropertyPath(paramName);
        if (StringUtils.isEmpty(compPropertiesPath)) {
            return componentProperties;
        }
        NamedThing property = componentProperties.getProperty(compPropertiesPath);
        if (property == null) {
            return getCurrentComponentPropertiesSpecial(componentProperties, paramName);
        }
        if (property instanceof ComponentProperties) {
            return (ComponentProperties) property;
        }
        return null;
    }

    private static ComponentProperties getCurrentComponentPropertiesSpecial(ComponentProperties componentProperties,
            String paramName) {
        ComponentProperties currentComponentProperties = null;
        if (componentProperties == null || paramName == null) {
            return null;
        }
        List<NamedThing> allProps = componentProperties.getProperties();
        for (NamedThing namedThing : allProps) {
            if (paramName.equals(namedThing.getName())) {
                currentComponentProperties = componentProperties;
                break;
            }
            if (namedThing instanceof ComponentProperties) {
                ComponentProperties childComponentProperties = (ComponentProperties) namedThing;
                currentComponentProperties = getCurrentComponentProperties(childComponentProperties, paramName);
            }
            if (currentComponentProperties != null) {
                break;
            }
        }
        return currentComponentProperties;
    }

    public static NamedThing getGenericSchemaElement(ComponentProperties componentProperties, String paramName) {
        if (componentProperties == null || paramName == null) {
            return null;
        }
        return componentProperties.getProperty(paramName);
    }

    public static Object getGenericPropertyValue(ComponentProperties componentProperties, String paramName) {
        if (componentProperties == null || paramName == null) {
            return null;
        }
        ComponentProperties currentComponentProperties = getCurrentComponentProperties(componentProperties, paramName);
        if (currentComponentProperties == null) {
            return null;
        }
        Property property = componentProperties.getValuedProperty(paramName);
        if (property != null) {
            return property.getValue();
        }
        return null;
    }

    public static void setGenericPropertyValue(ComponentProperties componentProperties, String paramName, Object value) {
        if (componentProperties == null || paramName == null) {
            return;
        }
        ComponentProperties currentComponentProperties = getCurrentComponentProperties(componentProperties, paramName);
        if (currentComponentProperties == null) {
            return;
        }
        currentComponentProperties.setValue(getPropertyName(paramName), value);
    }

    /**
     * looks for all Property type properties of the given componenetProperties and all is nested CompoenetProperties.
     */
    public static List<Property> getAllValuedProperties(ComponentProperties componentProperties) {
        List<Property> allValuedProperties = new ArrayList<>();
        if (componentProperties == null) {
            return null;
        }
        List<NamedThing> namedThings = componentProperties.getProperties();
        for (NamedThing namedThing : namedThings) {
            if (namedThing instanceof ComponentProperties) {
                ComponentProperties childComponentProperties = (ComponentProperties) namedThing;
                allValuedProperties.addAll(getAllValuedProperties(childComponentProperties));
            } else if (namedThing instanceof Property) {
                allValuedProperties.add((Property) namedThing);
            } // else other type not yet handled
        }
        return allValuedProperties;
    }

    private static String getPropertyPath(String paramName) {
        String propertyPath = ""; //$NON-NLS-1$
        if (paramName.indexOf(IGenericConstants.EXP_SEPARATOR) != -1) {
            propertyPath = paramName.substring(0, paramName.lastIndexOf(IGenericConstants.EXP_SEPARATOR));
        }
        return propertyPath;
    }

    public static String getPropertyName(String paramName) {
        String propertyName = paramName;
        if (propertyName.indexOf(IGenericConstants.EXP_SEPARATOR) != -1) {
            propertyName = propertyName.substring(propertyName.lastIndexOf(IGenericConstants.EXP_SEPARATOR) + 1);
        }
        return propertyName;
    }

    public static JavaType getTalendTypeFromPropertyType(Property.Type type) {
        switch (type) {
        case BOOLEAN:
            return JavaTypesManager.BOOLEAN;
        case BYTE_ARRAY:
            return JavaTypesManager.BYTE_ARRAY;
        case DATE:
        case DATETIME:
            return JavaTypesManager.DATE;
        case DECIMAL:
            return JavaTypesManager.BIGDECIMAL;
        case DOUBLE:
            return JavaTypesManager.DOUBLE;
        case DYNAMIC:
            return JavaTypesManager.DYNAMIC;
        case FLOAT:
            return JavaTypesManager.FLOAT;
        case INT:
            return JavaTypesManager.INTEGER;
        default:
            return JavaTypesManager.STRING;
        }
    }

    public static boolean isSupportContext(Property schemaElement) {
        Property.Type type = schemaElement.getType();
        switch (type) {
        case STRING:
        case INT:
        case DATE:
        case DATETIME:
        case DECIMAL:
        case FLOAT:
        case DOUBLE:
            return true;
        default:
            break;
        }
        return false;
    }

    public static boolean isSameComponentProperties(ComponentProperties componentProperties, NamedThing widgetProperty) {
        if (componentProperties != null && widgetProperty instanceof Form) {
            Form subForm = (Form) widgetProperty;
            if (subForm != null) {
                return componentProperties == subForm.getProperties();
            }
        }
        return false;
    }

    public static ComponentProperties getComponentPropertiesFromSerialized(String serialized) {
        if (serialized != null) {
            Deserialized<ComponentProperties> fromSerialized = ComponentProperties.fromSerialized(serialized,
                    ComponentProperties.class);
            if (fromSerialized != null) {
                return fromSerialized.properties;
            }
        }
        return null;
    }

    /**
     * Check if the current trigger contains correct information to be translated to a NodeConnector. For example, we
     * currently do not support LOOKUP or MERGE trigger.
     *
     * @param trigger a Trigger generated by the new component architecture
     * @param componentName the name of the current component
     * @return a boolean if the trigger is valid
     */
    public static boolean isAValidTrigger(Trigger trigger, String componentName) {
        String triggerName = trigger.getType().name();
        EConnectionType currentType = EConnectionType.getTypeFromName(triggerName);
        if (currentType == null || ("LOOKUP").equals(triggerName) || ("MERGE").equals(triggerName)) {//$NON-NLS-1$//$NON-NLS-2$
            if (currentType == null) {
                Component.getLog().warn(Messages.getString("Component.componentNotExist", componentName //$NON-NLS-1$
                        , triggerName));
            }
            return false;
        }
        return true;
    }

    /**
     * Transform a Trigger to a NodeConnector.
     *
     * @param trigger a Trigger generated by the new component architecture
     * @param parentNode The parent node current trigger
     * @return a NodeConnector compatible with the Studio.
     */
    public static INodeConnector generateNodeConnectorFromTrigger(Trigger trigger, INode parentNode) {
        String triggerName = trigger.getType().name();
        // set output
        if (!"ITERATE".equals(triggerName)) {//$NON-NLS-1$
            // only accept the definition of ITERATE for now.
            return null;
        }

        EConnectionType currentType = EConnectionType.getTypeFromName(triggerName);

        INodeConnector nodeConnector = new NodeConnector(parentNode);

        nodeConnector.setDefaultConnectionType(currentType);
        // set the default values
        nodeConnector.setLinkName(currentType.getDefaultLinkName());
        nodeConnector.setMenuName(currentType.getDefaultMenuName());

        // set input
        nodeConnector.setMaxLinkInput(trigger.getMaxInput());

        nodeConnector.setMaxLinkOutput(trigger.getMaxOutput());

        if (nodeConnector.getName() == null) {
            nodeConnector.setName(triggerName);
            nodeConnector.setBaseSchema(currentType.getName());
        }

        setConnectionProperty(currentType, nodeConnector);
        return nodeConnector;
    }

    /**
     * Utilitary function to set a connectionProperty of a given type to a node.
     *
     * @param currentType type of the connection
     * @param node currentNode
     */
    private static void setConnectionProperty(EConnectionType currentType, INodeConnector node) {
        // One line method that factorize a lot of code.
        node.addConnectionProperty(currentType, currentType.getRGB(), currentType.getDefaultLineStyle());
    }
}
