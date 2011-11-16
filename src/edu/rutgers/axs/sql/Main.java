/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.    
 */
package edu.rutgers.axs.sql;

import java.util.*;
import java.lang.reflect.*;
import javax.persistence.*;


/** 
 * A very simple, stand-alone program that stores a new entity in the
 * database and then performs a query to retrieve it.
 */
public class Main {

    /** This name will be used to configure the EntityManagerFactory
	based on the corresponding name in the
	META-INF/persistence.xml file
     */
    final public static String persistenceUnitName = "arxiv";

    private static EntityManagerFactory factory = null;

    public
    static synchronized EntityManager getEM() {
        // Create a new EntityManagerFactory using the System properties.
        // The "icd" name will be used to configure based on the
        // corresponding name in the META-INF/persistence.xml file
	if (factory == null) {
	    factory = Persistence.
		createEntityManagerFactory(persistenceUnitName,
					   System.getProperties());
	}

        // Create a new EntityManager from the EntityManagerFactory. The
        // EntityManager is the main object in the persistence API, and is
        // used to create, delete, and query objects, as well as access
        // the current transaction
        EntityManager em = factory.createEntityManager();
	return em;
    }



}
