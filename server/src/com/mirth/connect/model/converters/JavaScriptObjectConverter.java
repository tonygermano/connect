/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.model.converters;

import java.util.concurrent.atomic.AtomicReference;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

import com.mirth.connect.server.controllers.ContextFactoryController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.util.javascript.JavaScriptScopeUtil;
import com.mirth.connect.server.util.javascript.MirthContextFactory;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;

public class JavaScriptObjectConverter extends ReflectionConverter {
       
    private static class DelayedInitializedValues {
        private static final ContextFactoryController contextFactoryController = ControllerFactory.getFactory().createContextFactoryController();
        private static final MirthContextFactory mirthContextFactory = contextFactoryController.getGlobalContextFactory();
    }
    
    // We don't want the quotes around the date, so call toISOString directly
    private static final String conversionScript = "value instanceof Date ? value.toISOString() : JSON.stringify(value);";
    
    private static final AtomicReference<Script> compiledConversionScript = new AtomicReference<>();

    public JavaScriptObjectConverter(Mapper mapper) {
        super(mapper, JVM.newReflectionProvider());
    }

    @Override
    public boolean canConvert(Class type) {
        return Scriptable.class.isAssignableFrom(type);
    }

    @Override
    public void marshal(Object value, HierarchicalStreamWriter writer, MarshallingContext context) {
        try {
            final Scriptable scope = JavaScriptScopeUtil.getDeployScope(DelayedInitializedValues.mirthContextFactory, null);
            final Context jsContext = Context.getCurrentContext();
            final Script script = getCompiledScript(jsContext);
            scope.put("value", scope, value);
            context.convertAnother(script.exec(jsContext, scope));
        } catch (Exception e) {
            super.marshal(value, writer, context);
        } finally {
            Context.exit();
        }
    }

    private Script getCompiledScript(Context context) {
        Script script = compiledConversionScript.get();
        if (script == null) {
            script = context.compileString(conversionScript, "JSON stringifier", 1, null);
            compiledConversionScript.compareAndSet(null, script);
            script = compiledConversionScript.get();
        }
        return script;
    }
}
