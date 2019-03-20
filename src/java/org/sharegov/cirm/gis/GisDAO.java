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
package org.sharegov.cirm.gis;

import static org.sharegov.cirm.rdb.Sql.SELECT;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mjson.Json;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.sharegov.cirm.CirmTransaction;
import org.sharegov.cirm.OWL;
import org.sharegov.cirm.Refs;
import org.sharegov.cirm.rdb.Concepts;
import org.sharegov.cirm.rdb.Query;
import org.sharegov.cirm.rdb.RelationalOWLMapper;
import org.sharegov.cirm.rdb.RelationalStore;
import org.sharegov.cirm.rdb.Sql;
import org.sharegov.cirm.rest.OperationService;
import org.sharegov.cirm.utils.GenUtils;
import org.sharegov.cirm.utils.ThreadLocalStopwatch;
import static org.sharegov.cirm.utils.GenUtils.*;

/**
 * <p>
 * Manage GIS data in the relational database. GIS layers come as a large JSON structure which
 * we save as a blob. Saving GIS data is necessary for auditing and reporting purposes mainly
 * because a lot of it is time sensitive (e.g. current commissioner of a district). Some
 * of the fields are relationalized to facilitate database searches. 
 * </p>
 * @author Borislav Iordanov
 *
 */
public class GisDAO
{
    private static volatile Json columns;

    private static String getColumnTypeName(Json coltype)
    {
        if (coltype.isObject())
            return coltype.at("label").asString();
        else
            return coltype.asString().split("#")[1];
    }
    
    /**
     * Retrieves locationInfo from MDCGIS, and ensures it is saved in db, then returnes id.
     * (Not used)
     * @param x
     * @param y
     * @return
     */
    public long getGisDBId(double x, double y)
    {
        return getGisDBId(Refs.gisClient.resolve().getLocationInfo(x, y, null, 3, 500), false);
    }
    
    public static Json getGisData(String gisDBID)
    {
        try
        {
            RelationalStore store = Refs.defaultRelationalStore.resolve();
            Sql select = SELECT();
            org.sharegov.cirm.rdb.Statement statement = new org.sharegov.cirm.rdb.Statement();
            Query query = new Query();
            query.setStatement(statement);
            statement.setSql(select);
            select
                .COLUMN("DATA")
                .FROM("CIRM_GIS_INFO")
                .WHERE("ID")
                .EQUALS(gisDBID);
            Json result = store.customSearch(query);
            if (result.asJsonList().isEmpty())
                return Json.nil();
            else
                return Json.read(result.at(0).at("DATA").asString());
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Get a row of normalized Gis Data, except the data clob.
     * @param gisDBID
     * @return a json with all normalized columns, except the data clob
     */
    public static Json getGisDataColumns(String gisDBID, String...columns)
    {
        try
        {
            RelationalStore store = OperationService.getPersister().getStore();
            Sql select = SELECT();
            org.sharegov.cirm.rdb.Statement statement = new org.sharegov.cirm.rdb.Statement();
            Query query = new Query();
            query.setStatement(statement);
            statement.setSql(select);
            for (String column : columns)
            {
                select.COLUMN(column);
            };
            select
                .FROM("CIRM_GIS_INFO")
                .WHERE("ID")
                .EQUALS(gisDBID);
            return store.customSearch(query);
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private static long ensureInDB(Json locationInfo, boolean directMapping)
    {
        if(dbg())
            ThreadLocalStopwatch.getWatch().time("In GisClient.ensureInDB() start.");
        String normalizedData = GenUtils.normalizeAsString(locationInfo);
        String hash = OWL.hash(normalizedData);
        Connection conn = null;
        PreparedStatement pstmt = null;
        Statement stmt = null;
        ResultSet rs = null;
        Json col = getColumns();
        try
        {
            
            if(dbg())
                ThreadLocalStopwatch.getWatch().time("GisClient.ensureInDB() find by hash");
            conn = Refs.defaultRelationalStoreExt.resolve().getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select * from CIRM_GIS_INFO where hash='" + hash + "'");
            while (rs.next())
            {
                String s = rs.getString(3);
                Json existing = Json.read(s);
                if (locationInfo.equals(existing))
                    return rs.getLong(1);
            }
            rs.close();
            rs = null;
            stmt.close();
            if(dbg())
                ThreadLocalStopwatch.getWatch().time("GisClient.ensureInDB() hash not found, map columns and save start.");
            long id = Refs.idFactory.resolve().generateSequenceNumber();
            StringBuilder insert = new StringBuilder("insert into CIRM_GIS_INFO (");
            StringBuilder values = new StringBuilder(") VALUES (");
            if(dbg())
                ThreadLocalStopwatch.getWatch().time("GisClient.ensureInDB() reasoner START queryIndividuals and toJson.");
            for (int i = 0; i < col.asJsonList().size(); i++ )
            {
                String name = col.at(i).at("label").asString().split("\\.")[1];
                insert.append(name);
                values.append("?");
                if (i < col.asJsonList().size() - 1)
                {
                    insert.append(",");
                    values.append(",");
                }
            }
            if(dbg())
                ThreadLocalStopwatch.getWatch().time("GisClient.ensureInDB() reasoner END queryIndividuals and toJson.");
            insert.append(values);
            insert.append(")");
            if(dbg())
                System.out.println("Insert: " + insert.toString());
            pstmt = conn.prepareStatement(insert.toString());//conn.prepareStatement("insert into CIRM_GIS_INFO VALUES(?,?,?)");
            for (int i = 0; i < col.asJsonList().size(); i++ )
            {
                String name = col.at(i).at("label").asString().split("\\.")[1];
                if ("ID".equals(name))
                    pstmt.setLong(i + 1, id);
                else if ("HASH".equals(name))                   
                    pstmt.setString(i + 1, hash);
                else if ("DATA".equals(name))
                    pstmt.setString(i + 1, normalizedData);
                else if (directMapping || col.at(i).has("hasGeoAttribute"))
                {
                    Json value = null;
                    if(directMapping)
                    {
                        String legacyGeoName = name.substring("GIS_".length());
                        if (!locationInfo.has(legacyGeoName))
                        {
                            pstmt.setObject(i + 1, null);
                            continue;
                        }
                        else
                            value = locationInfo.at(legacyGeoName);
                        
                    }
                    else 
                    {
                        String layerName = col.at(i).at("hasGeoAttribute").at("hasGisLayer").at("hasName").asString();
                        String attrname = col.at(i).at("hasGeoAttribute").at("hasName").asString();
                        if (!locationInfo.has(layerName))
                        {
                            pstmt.setObject(i + 1, null);
                            continue;
                        }
                        else
                        {
                            Json ldata = locationInfo.at(layerName);
                            value = null;
                            if (ldata.isObject())
                            {
                                if(ldata.has(attrname))
                                    value = ldata.at(attrname);
                                else 
                                    value = null;
                            }
                            else if (ldata.isArray() && ldata.asJsonList().size() > 0 && ldata.at(0).isObject())
                                value = ldata.at(0).at(attrname);
                        }
                    }
                    if (value == null)
                        pstmt.setObject(i + 1, null);
                    else
                    {
                        // this is necessary because the column type may be serialized several times
                        // within the larger JSON structure and it will appear in full only in one place
                        // while the other copies will just be the IRI (a JSON string). This so that
                        // we can transmit circular JSON structures to the client. Ideally, when working
                        // with Json at the sever, the structure should be fully circular. So the solution
                        // is the let the circularity be removed at the JsonEntityProvider. When this is
                        // done, getColumnTypeName will not be needed anymore.
                        if (!col.at(i).has("hasColumnType"))
                            throw new NullPointerException("Missing column type for " + col.at(i));
                        String typeName = getColumnTypeName(col.at(i).at("hasColumnType"));
                        if ("VARCHAR".equals(typeName))
                            pstmt.setString(i + 1, value.asString());
                        else if ("INTEGER".equals(typeName))
                            pstmt.setInt(i + 1, value.asInteger());
                        else if ("DOUBLE".equals(typeName))
                            pstmt.setDouble(i + 1, value.asDouble());                       
                        else
                            pstmt.setObject(i+1, value.getValue());
                    }
                }
                else
                    pstmt.setObject(i+1, null);
            }           
            pstmt.execute();
            conn.commit();
            if(dbg())
                ThreadLocalStopwatch.getWatch().time("GisClient.ensureInDB() map columns and save end.");
            return id;
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
        finally
        {
            if (rs != null)
                try { rs.close(); } catch (Throwable t) { } 
            if (stmt != null)
                try { stmt.close(); } catch (Throwable t) { }
            if (pstmt != null)
                try { pstmt.close(); } catch (Throwable t) { }                      
            if (conn != null)
                try { conn.close(); } catch (Throwable t) { } 
            if(dbg())
                ThreadLocalStopwatch.getWatch().time("GisClient.ensureInDB() finished.");
        }       
    }
    
    /**
     * 
     * @param locationInfo - GIS information structured as Json.
     * @param directMapping - when set to true implies that the attributes in locationInfo match the column
     *                        names in the DB table, this occurs when retrieving GIS data directly from CSR.
     * @return
     */
    public static long getGisDBId(final Json locationInfo, final boolean directMapping)
    {
        return Refs.defaultRelationalStore.resolve().txn(new CirmTransaction<Long>() {
            public Long call()
            {
                return ensureInDB(locationInfo, directMapping); 
            }
        });
    }
    
    /**
     * Thread safe getColumns method.
     */
    private static Json getColumns()
    {
        Json result = null;
        if(columns == null)
        {
            synchronized (GisDAO.class)
            {
                if(columns == null)
                {
                    if(dbg())
                        ThreadLocalStopwatch.getWatch().time("GisClient.getColumns() synchronized block started.");
                    result = Json.array();
                    for (OWLNamedIndividual col : RelationalOWLMapper.columns(OWL.individual("CIRM_GIS_INFO")))
                        result.add(OWL.toJSON(Refs.topOntology.resolve(), col));
                    for (int i = 0; i < result.asJsonList().size(); i++ )
                    {                       
                            
                        String name = result.at(i).at("label").asString().split("\\.")[1];
                        if (name.startsWith("GIS"))
                        {
                            Set<OWLNamedIndividual> S = OWL.queryIndividuals(
                                        "GeoLayerAttribute and hasColumnMapping value " + 
                                                result.at(i).at("label").asString());
                            if (S.isEmpty())
                            {
                                System.err.println("WARN - no geo attribute for GIS column " + result.at(i));
    
                            }
                            else
                            {
                                result.at(i).set("hasGeoAttribute", 
                                    
                                        OWL.toJSON(Refs.topOntology.resolve(),S.iterator().next()));
                            }
                        }
                    }
                    columns = result;
                    if(dbg())
                        ThreadLocalStopwatch.getWatch().time("GisClient.getColumns() synchronized block finished.");
                }
            }
        }
        return columns;
    }
    public static void updateNormalizedColumns(long dbId, Json locationInfo, boolean directMapping) 
    {
    	updateNormalizedColumns(dbId, locationInfo, null, directMapping);
    }
    
    public static void updateNormalizedColumns(long dbId, Json locationInfo, List<String> columns, boolean directMapping)
    {
        Json col = getColumns();
        try
        {
            Sql update = Sql.UPDATE("CIRM_GIS_INFO");
            List<Object> parameters = new ArrayList<Object>();
            List<OWLNamedIndividual> parameterTypes = new ArrayList<OWLNamedIndividual>();
            for( int i = 0; i < col.asJsonList().size(); i++)
            {
                String name = col.at(i).at("label").asString().split("\\.")[1];
                if (!("ID".equals(name)|| "HASH".equals(name) ||"DATA".equals(name)))
                {
                	if (columns == null || columns.contains(name)) 
                	{
                        update.SET(name, "?");
                        if (!col.at(i).has("hasColumnType"))
                            throw new NullPointerException("Missing column type for " + col.at(i));
                        String typeName = getColumnTypeName(col.at(i).at("hasColumnType"));
                        if ("VARCHAR".equals(typeName))
                            parameterTypes.add(OWL.individual(Concepts.VARCHAR));
                        else if ("INTEGER".equals(typeName))
                            parameterTypes.add(OWL.individual(Concepts.INTEGER));
                        else if ("DOUBLE".equals(typeName))
                            parameterTypes.add(OWL.individual(Concepts.DOUBLE));                
                        else
                            parameterTypes.add(OWL.individual(Concepts.VARCHAR));
                        if (directMapping || col.at(i).has("hasGeoAttribute"))
                        {
                            Json value = null;
                            if(directMapping)
                            {
                                String legacyGeoName = name.substring("GIS_".length());
                                if (!locationInfo.has(legacyGeoName))
                                {
                                    parameters.add(null);
                                    
                                    continue;
                                }
                                else
                                    value = locationInfo.at(legacyGeoName);
                                
                            }
                            else 
                            {
                                String layerName = col.at(i).at("hasGeoAttribute").at("hasGisLayer").at("hasName").asString();
                                String attrname = col.at(i).at("hasGeoAttribute").at("hasName").asString();
                                Json layerJson = null;
                                if (locationInfo.has(layerName)) {
                                	layerJson = locationInfo.at(layerName);
                                } 
                                else if (locationInfo.has("address") 
                                		&& locationInfo.at("address").isObject() 
                                		&& locationInfo.at("address").has(layerName)) 
                                {
                                	layerJson = locationInfo.at("address").at(layerName);
                                }
                                if (layerJson != null )                               
                                {
                                    value = null;
                                    if (layerJson.isObject())
                                    {
                                        if(layerJson.has(attrname))
                                            value = layerJson.at(attrname);
                                        else 
                                            value = null;
                                    }
                                    else if (layerJson.isPrimitive()) 
                                    {
                                    	value = layerJson;
                                    }                                    
                                    else if (layerJson.isArray() && layerJson.asJsonList().size() > 0 && layerJson.at(0).isObject()) 
                                    {
                                        value = layerJson.at(0).at(attrname);
                                    }
                                }
                            }
                            parameters.add((value != null)?value.asString():null);
                        }
                        else {
                            parameters.add(null);
                        }
                    }
                }
            }
            update.WHERE("ID").EQUALS(Long.toString(dbId));
            if(dbg())
                System.out.println("Update: " + update.SQL());
            org.sharegov.cirm.rdb.Statement stmt = new org.sharegov.cirm.rdb.Statement();
            stmt.setSql(update);
            stmt.setParameters(parameters);
            stmt.setTypes(parameterTypes);
            Refs.defaultRelationalStoreExt.resolve().executeStatement(stmt);
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}
