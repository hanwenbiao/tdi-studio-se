// ============================================================================
//
// Copyright (C) 2006-2015 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.designer.core.generic.context;

import java.util.List;

import org.apache.avro.Schema;
import org.apache.commons.lang3.ClassUtils;
import org.talend.core.model.process.IContext;
import org.talend.core.model.process.IContextManager;
import org.talend.core.model.process.INode;
import org.talend.core.model.process.IProcess;
import org.talend.core.model.utils.ContextParameterUtils;
import org.talend.core.runtime.util.GenericTypeUtils;
import org.talend.daikon.exception.TalendRuntimeException;
import org.talend.daikon.properties.EnumProperty;
import org.talend.daikon.properties.Property;
import org.talend.daikon.properties.PropertyValueEvaluator;

/**
 * created by ycbai on 2016年2月6日 Detailled comment
 *
 */
public class ComponentContextPropertyValueEvaluator implements PropertyValueEvaluator {

    private INode node;

    public ComponentContextPropertyValueEvaluator(INode node) {
        this.node = node;
    }

    @Override
    public Object evaluate(Property property, Object storedValue) {
        if (storedValue == null) {
            return storedValue;
        }
        List<?> possibleValues = ((Property) property).getPossibleValues();
        if (possibleValues != null) {
            if (storedValue instanceof String && !ContextParameterUtils.isContainContextParam((String) storedValue)) {
                for (Object possibleValue : possibleValues) {
                    if (possibleValue.toString().equals(storedValue)) {
                        return possibleValue;
                    }
                }
            }
        }
        if (storedValue instanceof Schema || storedValue instanceof List || storedValue instanceof Enum
                || storedValue instanceof Boolean) {
            return storedValue;
        }
        IContext context = null;
        if (node != null) {
            IProcess process = node.getProcess();
            if (process != null) {
                IContextManager cm = process.getContextManager();
                if (cm != null) {
                    context = cm.getDefaultContext();
                }
            }
        }
        if (context != null) {
            String valueFromContext = ContextParameterUtils.parseScriptContextCode(String.valueOf(storedValue), context);
            if (GenericTypeUtils.isBooleanType(property)) {
                return new Boolean(valueFromContext);
            }
            if (GenericTypeUtils.isIntegerType(property) && !valueFromContext.isEmpty()) {
                try {
                    return Integer.valueOf(valueFromContext);
                } catch (Exception e) {
                    // context value not existing anymore
                    // return any value to let the component work without exception
                    return 0;
                }
            }
            if (GenericTypeUtils.isEnumType(property)) {
                List<?> propertyPossibleValues = ((Property<?>) property).getPossibleValues();
                if (propertyPossibleValues != null) {
                    for (Object possibleValue : propertyPossibleValues) {
                        if (possibleValue.toString().equals(valueFromContext)) {
                            return possibleValue;
                        }
                    }
                }
            }
            if (valueFromContext != null) {
                return valueFromContext;
            }
        }
        return storedValue;
    }

}
