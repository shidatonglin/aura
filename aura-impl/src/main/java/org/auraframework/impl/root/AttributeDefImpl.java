/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.impl.root;

import org.auraframework.def.AttributeDef;
import org.auraframework.def.AttributeDefRef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.DefinitionAccess;
import org.auraframework.def.RootDefinition;
import org.auraframework.def.TypeDef;
import org.auraframework.impl.system.DefinitionImpl;
import org.auraframework.system.Location;
import org.auraframework.throwable.quickfix.InvalidDefinitionException;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.auraframework.util.AuraTextUtil;
import org.auraframework.util.json.Json;
import org.auraframework.validation.ReferenceValidationContext;

import java.io.IOException;
import java.util.Set;

/**
 * The definition of an attribute. Holds all information about a given component's AttributeDefRef, aside from the
 * actual ValueDefRef. AttributeInfos are immutable. Once they are created, they can only be replaced, never changed.
 */
public final class AttributeDefImpl extends DefinitionImpl<AttributeDef> implements AttributeDef {

    /**
     * Construct an AttributeDef
     *
     * @param descriptor the descriptor of this attribute
     * @param parentDescriptor the parent descriptor of this attribute
     * @param typeDefDescriptor The TypeDef Descriptor for the Type of instances of this AttributeDef
     * @param defaultValue The ValueDef for the default value to be used if no Value is set on the Attribute instance
     * @param required true if is required that this attribute Value be set (not defaulted) on Attribute instances that
     *            refer to this AttributeDef
     * @param location The location where this AttributeDef was defined in the markup.
     * @param access the access value for the attribute
     */
    public AttributeDefImpl(DefDescriptor<AttributeDef> descriptor,
                            DefDescriptor<? extends RootDefinition> parentDescriptor, DefDescriptor<TypeDef> typeDefDescriptor,
                            AttributeDefRef defaultValue, boolean required, SerializeToType serializeTo, Location location,
                            DefinitionAccess access) {
        super(descriptor, location, access);
        this.parentDescriptor = parentDescriptor;
        this.typeDefDescriptor = typeDefDescriptor;
        this.defaultValue = defaultValue;
        this.required = required;
        this.serializeTo = serializeTo;
    }

    protected AttributeDefImpl(Builder builder) {
        super(builder);
        this.parentDescriptor = builder.parentDescriptor;
        this.typeDefDescriptor = builder.typeDefDescriptor;
        this.defaultValue = builder.defaultValue;
        this.required = builder.required;
        this.serializeTo = builder.serializeTo;
    }

    /**
     * @return The TypeDef that defines type information about Values for instances of this AttributeDef
     * @throws QuickFixException
     * @deprecated use #getTypeDesc()
     */
    @Override
    @Deprecated
    public TypeDef getTypeDef() throws QuickFixException {
        if (typeDefDescriptor == null) {
            return null;
        }
        return typeDefDescriptor.getDef();
    }

    @Override
    public DefDescriptor<TypeDef> getTypeDesc() {
        return typeDefDescriptor;
    }

    /**
     * @return The default value to be used for instances of this AttributeDef that do not have a Value explicitly set
     */
    @Override
    public AttributeDefRef getDefaultValue() {
        return defaultValue;
    }

    /**
     * @return True if instances must require a value to be explicitly set
     */
    @Override
    public boolean isRequired() {
        return required;
    }

    /**
     * @return SERVER if this attribute should only be serialized from client to server, BOTH if serialization should
     *         occur in both directions (the default)
     */
    @Override
    public SerializeToType getSerializeTo() {
        return serializeTo != null ? serializeTo : SerializeToType.BOTH;
    }

    @Override
    public void serialize(Json json) throws IOException {
        json.writeLiteral("[");
        // 0: name
        json.writeValue(descriptor);
        json.writeLiteral(",");

        // 1: type
        json.writeValue(typeDefDescriptor);
        json.writeLiteral(",");

        // 2: access
        json.writeValue(getAccess().getAccessCode());
        json.writeLiteral(",");

        // 3: required
        json.writeValue(required);

        // 4: default
        if (defaultValue != null) {
            json.writeLiteral(",");
            json.writeValue(defaultValue.getValue());
        }
        else if (typeDefDescriptor != null) {
            if (typeDefDescriptor.getQualifiedName().equals("aura://List") || typeDefDescriptor.getName().endsWith("[]")) {
                json.writeLiteral(",[]");
            }
            else if (!typeDefDescriptor.getQualifiedName().equals("aura://Boolean") &&
                    !typeDefDescriptor.getQualifiedName().equals("aura://String") &&
                    !typeDefDescriptor.getQualifiedName().equals("aura://Decimal") &&
                    !typeDefDescriptor.getQualifiedName().equals("aura://Number") &&
                    !typeDefDescriptor.getQualifiedName().equals("aura://Integer")) {
                json.writeLiteral(",");
                json.writeValue(null);
            }
        }
        json.writeLiteral("]");
    }

    @Override
    public void appendDependencies(Set<DefDescriptor<?>> dependencies) {
        if (defaultValue != null) {
            defaultValue.appendDependencies(dependencies);
        }

        dependencies.add(typeDefDescriptor);
    }

    @Override
    public void validateDefinition() throws QuickFixException {
        super.validateDefinition();

        if (this.typeDefDescriptor == null) {
            throw new InvalidDefinitionException("Invalid typeDefDescriptor: null", getLocation());
        }

        String name = this.descriptor.getName();
        // Calls the validateAttributeName method in AuraTextUtil.java to check if its a valid attribute name
        if ((AuraTextUtil.validateAttributeName(name)) != true) {
            throw new InvalidDefinitionException("Invalid attribute name: '" + name
                    + "', Refer to AuraDocs for valid attribute names", getLocation());
        }

        if (this.serializeTo == SerializeToType.INVALID) {
            throw new InvalidDefinitionException("Invalid serializeTo value", getLocation());
        }
    }

    @Override
    public void validateReferences(ReferenceValidationContext validationContext) throws QuickFixException {
        super.validateReferences(validationContext);
        TypeDef typeDef = validationContext.getAccessibleDefinition(typeDefDescriptor);
        if (defaultValue != null) {
            // FIXME(goliver): It is possible that we should repackage exceptions from
            // parseValue, but let's not try to be clever right now. This code goes via
            // a rather circuitious path that we should probably clean up.
            defaultValue.parseValue(typeDef);

            defaultValue.validateReferences(validationContext);
        }
    }

    /**
     * @return Returns the parentDescriptor.
     */
    @Override
    public DefDescriptor<? extends RootDefinition> getParentDescriptor() {
        return parentDescriptor;
    }

    public static class Builder extends DefinitionImpl.BuilderImpl<AttributeDef> {

        public Builder() {
            super(AttributeDef.class);
        }

        public DefDescriptor<TypeDef> typeDefDescriptor;
        public AttributeDefRef defaultValue;
        public SerializeToType serializeTo;
        public String minVersion = null;
        public String maxVersion = null;

        private DefDescriptor<? extends RootDefinition> parentDescriptor;
        private boolean required;

        /**
         * @see org.auraframework.impl.system.DefinitionImpl.BuilderImpl#build()
         */
        @Override
        public AttributeDefImpl build() {
            return new AttributeDefImpl(this);
        }

        /**
         * Sets the parentDescriptor for this instance.
         *
         * @param parentDescriptor The parentDescriptor.
         */
        public Builder setParentDescriptor(DefDescriptor<? extends RootDefinition> parentDescriptor) {
            this.parentDescriptor = parentDescriptor;
            return this;
        }

        /**
         * Sets the typeDefDescriptor for this instance.
         *
         * @param typeDefDescriptor The typeDefDescriptor.
         */
        public Builder setTypeDefDescriptor(DefDescriptor<TypeDef> typeDefDescriptor) {
            this.typeDefDescriptor = typeDefDescriptor;
            return this;
        }

        /**
         * Sets the defaultValue for this instance.
         *
         * @param defaultValue The defaultValue.
         */
        public Builder setDefaultValue(AttributeDefRef defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }


        /**
         * Sets whether or not this instance is required.
         *
         * @param required The required.
         */
        public Builder setRequired(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Sets how this attribute should be serialized (to client, to server, or both (default)).
         */
        public Builder setSerializeTo(SerializeToType serializeTo) {
            this.serializeTo = serializeTo;
            return this;
        }
    }

    private static final long serialVersionUID = 2241357665688011566L;
    private final DefDescriptor<? extends RootDefinition> parentDescriptor;
    private final DefDescriptor<TypeDef> typeDefDescriptor;
    private final AttributeDefRef defaultValue;
    private final boolean required;
    private final SerializeToType serializeTo;

}
