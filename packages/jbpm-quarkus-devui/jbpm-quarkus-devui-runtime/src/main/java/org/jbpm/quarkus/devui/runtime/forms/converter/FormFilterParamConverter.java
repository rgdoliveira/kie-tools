/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jbpm.quarkus.devui.runtime.forms.converter;

import java.util.Collections;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.jbpm.quarkus.devui.runtime.forms.model.FormFilter;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class FormFilterParamConverter implements ParamConverter<FormFilter> {
    public FormFilter fromString(String names) {
        StringTokenizer stringTokenizer = new StringTokenizer(names, ";");
        return new FormFilter(Collections.list(stringTokenizer).stream().map(s -> (String) s).collect(Collectors.toList()));
    }

    public String toString(FormFilter names) {
        return names.toString();
    }
}
