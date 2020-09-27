/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.http;

import static org.apache.iotdb.db.utils.EnvironmentUtils.TEST_QUERY_CONTEXT;
import static org.junit.Assert.assertEquals;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.apache.iotdb.db.engine.StorageEngine;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.exception.metadata.IllegalPathException;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.db.http.constant.HttpConstant;
import org.apache.iotdb.db.http.router.Router;
import org.apache.iotdb.db.metadata.MManager;
import org.apache.iotdb.db.metadata.PartialPath;
import org.apache.iotdb.db.metadata.mnode.MNode;
import org.apache.iotdb.db.metadata.mnode.MeasurementMNode;
import org.apache.iotdb.db.qp.physical.crud.InsertRowPlan;
import org.apache.iotdb.db.qp.physical.crud.RawDataQueryPlan;
import org.apache.iotdb.db.query.executor.QueryRouter;
import org.apache.iotdb.db.service.IoTDB;
import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.common.constant.TsFileConstant;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;
import org.apache.iotdb.tsfile.write.record.TSRecord;
import org.apache.iotdb.tsfile.write.record.datapoint.DoubleDataPoint;
import org.junit.Assert;

// prepare data for http test

public abstract class HttpPrepData {

  private String processorName = "root.test";
  protected static String[] measurements = new String[10];
  static {
    for (int i = 0; i < 10; i++) {
      measurements[i] = "m" + i;
    }
  }

  private MNode deviceMNode;
  protected TSDataType dataType = TSDataType.DOUBLE;
  protected TSEncoding encoding = TSEncoding.PLAIN;
  protected static MManager mmanager = IoTDB.metaManager;
  static Router router;
  static final String SUCCESSFUL_RESPONSE = "{\"result\":\"successful operation\"}";
  private final QueryRouter queryRouter = new QueryRouter();
  static final String LOGIN_URI = "/user/login?username=root&password=root";

  protected void prepareData() throws MetadataException, StorageEngineException {
    String test = "test";
    deviceMNode = new MNode(null, test);
    IoTDB.metaManager.setStorageGroup(new PartialPath(processorName));
    for (int i = 0; i < 10; i++) {
      deviceMNode.addChild(measurements[i], new MeasurementMNode(null, null, null, null));
      IoTDB.metaManager.createTimeseries(new PartialPath(processorName + TsFileConstant.PATH_SEPARATOR + measurements[i]), dataType,
          encoding, TSFileDescriptor.getInstance().getConfig().getCompressor(), Collections.emptyMap());
    }
    for (int i = 1; i <= 100; i++) {
      TSRecord record = new TSRecord(i, processorName);
      for (int j = 0; j < 10; j++) {
        record.addTuple(new DoubleDataPoint(measurements[j], i * 1.0));
      }
      insertToStorageEngine(record);
    }
  }

  private void insertToStorageEngine(TSRecord record)
      throws StorageEngineException, IllegalPathException {
    InsertRowPlan insertRowPlan = new InsertRowPlan(record);
    insertRowPlan.setDeviceMNode(deviceMNode);
    StorageEngine.getInstance().insert(insertRowPlan);
  }

  JSONArray postStorageGroupsJsonExample() {
    JSONArray jsonArray = new JSONArray();
    jsonArray.add("root.ln");
    jsonArray.add("root.sg");
    return jsonArray;
  }

  JSONArray deleteStorageGroupsJsonExample() {
    JSONArray jsonArray = new JSONArray();
    jsonArray.add(processorName);
    return jsonArray;
  }

  JSONArray createTimeSeriesJsonExample() {
    JSONArray jsonArray = new JSONArray();
    JSONObject timeSeries1 = new JSONObject();

    timeSeries1.put("timeSeries", "root.sg.d1.s1");
    timeSeries1.put("alias", "temperature");
    timeSeries1.put("dataType", "DOUBLE");
    timeSeries1.put("encoding", "PLAIN");

    JSONArray props = new JSONArray();
    JSONObject keyValue = new JSONObject();
    keyValue.put("key", "hello");
    keyValue.put("value", "world");
    props.add(keyValue);

    // same props
    timeSeries1.put("properties", props);
    timeSeries1.put("tags", props);
    timeSeries1.put("attributes", props);

    JSONObject timeSeries2 = new JSONObject();
    timeSeries2.put("timeSeries", "root.sg.d1.s2");
    timeSeries2.put("dataType", "DOUBLE");
    timeSeries2.put("encoding", "PLAIN");

    jsonArray.add(timeSeries1);
    jsonArray.add(timeSeries2);
    return jsonArray;
  }

  JSONArray deleteTimeSeriesJsonExample() {
    JSONArray timeSeries = new JSONArray();
    for (String measurement : measurements) {
      timeSeries.add(processorName + TsFileConstant.PATH_SEPARATOR + measurement);
    }
    return timeSeries;
  }

  void checkDataAfterDeletingTimeSeries() throws Exception {
    List<PartialPath> pathList = new ArrayList<>();
    pathList.add(new PartialPath(processorName + TsFileConstant.PATH_SEPARATOR + measurements[3]));
    pathList.add(new PartialPath(processorName + TsFileConstant.PATH_SEPARATOR + measurements[4]));
    pathList.add(new PartialPath(processorName + TsFileConstant.PATH_SEPARATOR + measurements[5]));
    List<TSDataType> dataTypes = new ArrayList<>();
    dataTypes.add(dataType);
    dataTypes.add(dataType);
    dataTypes.add(dataType);

    RawDataQueryPlan queryPlan = new RawDataQueryPlan();
    queryPlan.setDeduplicatedDataTypes(dataTypes);
    queryPlan.setDeduplicatedPaths(pathList);
    QueryDataSet dataSet = queryRouter.rawDataQuery(queryPlan, TEST_QUERY_CONTEXT);
    int count = 0;
    while (dataSet.hasNext()) {
      dataSet.next();
      count++;
    }
    assertEquals(0, count);
  }

  void buildMetaDataForGetTimeSeries() throws Exception{
    mmanager.setStorageGroup(new PartialPath("root.laptop"));
    CompressionType compressionType = TSFileDescriptor.getInstance().getConfig().getCompressor();
    mmanager.createTimeseries(new PartialPath("root.laptop.d1.s1"), TSDataType.valueOf("INT32"),
        TSEncoding.valueOf("RLE"), compressionType, Collections.emptyMap());
    mmanager.createTimeseries(new PartialPath("root.laptop.d1.1_2"), TSDataType.INT32, TSEncoding.RLE,
        TSFileDescriptor.getInstance().getConfig().getCompressor(), new HashMap<>());
    mmanager.createTimeseries(new PartialPath("root.laptop.d1.\"1.2.3\""), TSDataType.INT32, TSEncoding.RLE,
        TSFileDescriptor.getInstance().getConfig().getCompressor(), new HashMap<>());
  }

  JSONArray insertJsonExample(int i) {
    JSONArray inserts = new JSONArray();
    for(int j = 0; j < 6; j++) {
      JSONObject row = new JSONObject();
      row.put(HttpConstant.IS_NEED_INFER_TYPE, false);
      row.put(HttpConstant.DEVICE_ID, "root.ln.wf01.wt0" + i);
      JSONArray measurements = new JSONArray();
      measurements.add("temperature");
      measurements.add("status");
      measurements.add("hardware");
      row.put(HttpConstant.MEASUREMENTS, measurements);
      row.put(HttpConstant.TIMESTAMP, j);
      JSONArray values = new JSONArray();
      values.add(j);
      values.add(true);
      values.add(j);
      row.put(HttpConstant.VALUES, values);
      inserts.add(row);
    }
    return inserts;
  }

  void checkDataAfterInserting(int i) throws Exception{
    List<PartialPath> pathList = new ArrayList<>();
    pathList.add(new PartialPath("root.ln.wf01.wt0" + i +  ".temperature"));
    pathList.add(new PartialPath("root.ln.wf01.wt0" + i + ".status"));
    pathList.add(new PartialPath("root.ln.wf01.wt0" + i + ".hardware"));
    List<TSDataType> dataTypes = new ArrayList<>();
    dataTypes.add(TSDataType.INT32);
    dataTypes.add(TSDataType.BOOLEAN);
    dataTypes.add(TSDataType.INT32);

    RawDataQueryPlan queryPlan = new RawDataQueryPlan();
    queryPlan.setDeduplicatedDataTypes(dataTypes);
    queryPlan.setDeduplicatedPaths(pathList);
    QueryDataSet dataSet = queryRouter.rawDataQuery(queryPlan, TEST_QUERY_CONTEXT);
    int count = 0;
    while (dataSet.hasNext()) {
      dataSet.next();
      count++;
    }
    Assert.assertEquals(6, count);
  }

  JSONObject queryJsonExample() {
    JSONObject query = new JSONObject();

    //add timeSeries
    JSONArray timeSeries = new JSONArray();
    timeSeries.add(processorName + TsFileConstant.PATH_SEPARATOR + measurements[0]);
    timeSeries.add(processorName + TsFileConstant.PATH_SEPARATOR + measurements[9]);
    query.put(HttpConstant.TIME_SERIES, timeSeries);

    //set isAggregated
    query.put(HttpConstant.IS_AGGREGATED, true);

    // add functions
    JSONArray aggregations = new JSONArray();
    aggregations.add("COUNT");
    aggregations.add("COUNT");
    query.put(HttpConstant.AGGREGATIONS, aggregations);

    //set time filter
    query.put(HttpConstant.FROM, 1L);
    query.put(HttpConstant.TO, 20L);

    //Sets the number of partition
    query.put(HttpConstant.isPoint, true);

    //set Group by
    JSONObject groupBy = new JSONObject();
    groupBy.put(HttpConstant.SAMPLING_POINTS, 19);
    query.put(HttpConstant.GROUP_BY, groupBy);
    return query;
  }


}
