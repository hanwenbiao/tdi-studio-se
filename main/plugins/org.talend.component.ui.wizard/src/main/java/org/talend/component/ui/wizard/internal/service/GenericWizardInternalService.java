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
package org.talend.component.ui.wizard.internal.service;

import java.lang.reflect.Constructor;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.talend.commons.exception.ExceptionHandler;
import org.talend.component.ui.wizard.internal.IGenericWizardInternalService;
import org.talend.components.api.service.ComponentService;
import org.talend.components.api.wizard.ComponentWizard;
import org.talend.core.model.repository.ERepositoryObjectType;
import org.talend.repository.model.IRepositoryNode.ENodeType;
import org.talend.repository.model.IRepositoryNode.EProperties;
import org.talend.repository.model.RepositoryNode;

/**
 * created by ycbai on 2015年9月21日 Detailled comment
 *
 */
public class GenericWizardInternalService implements IGenericWizardInternalService {

    @Override
    public ComponentService getComponentService() {
        ComponentService compService = null;
        BundleContext bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        ServiceReference<ComponentService> compServiceRef = bundleContext.getServiceReference(ComponentService.class);
        if (compServiceRef != null) {
            compService = bundleContext.getService(compServiceRef);
        }
        return compService;
    }

    @Override
    public ComponentWizard getComponentWizard(String name) {
        return getComponentService().getComponentWizard(name, null);
    }

    @Override
    public RepositoryNode createRepositoryNode(RepositoryNode curParentNode, String label, ERepositoryObjectType type,
            ENodeType nodeType) {
        RepositoryNode dynamicNode = new RepositoryNode(null, (RepositoryNode) curParentNode.getRoot(), nodeType);
        dynamicNode.setProperties(EProperties.LABEL, label);
        dynamicNode.setProperties(EProperties.CONTENT_TYPE, type);
        curParentNode.getChildren().add(dynamicNode);
        return dynamicNode;
    }

    @Override
    public ERepositoryObjectType createRepositoryType(String type, String label, String alias, String folder, int ordinal) {
        Constructor<ERepositoryObjectType> dynamicConstructor = getConstructor(ERepositoryObjectType.class, new Class[] {
                String.class, String.class, String.class, String.class, int.class, boolean.class, String.class, String[].class,
                boolean.class, String[].class, boolean[].class });
        ERepositoryObjectType typeObject = null;
        try {
            dynamicConstructor.setAccessible(true);
            typeObject = dynamicConstructor.newInstance(type, label, folder, type, ordinal, false, alias,
                    new String[] { ERepositoryObjectType.PROD_DI }, false, new String[0], new boolean[] { true });
        } catch (Exception e) {
            ExceptionHandler.process(e);
        }
        return typeObject;
    }

    private <E> Constructor<E> getConstructor(Class<E> clazz, Class<?>[] argTypes) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                return (Constructor<E>) c.getDeclaredConstructor(argTypes);
            } catch (Exception e) {
                continue;
            }
        }
        return null;
    }

}