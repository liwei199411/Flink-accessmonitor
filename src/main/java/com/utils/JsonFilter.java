package com.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.events.AccessEvent;
import scala.Tuple6;

import java.util.Map;

public class JsonFilter {
    public String IsDdl = "false";
    public String TableName = "mj_event_report";
    public String Type = "INSERT";
    public String Data="data";
    public static String fieldDelimiter = ",";//字段分隔符，用于分隔Json解析后的字段

    public JsonFilter() {

    }

    public Boolean getJsonFilter(String string) {
        JSONObject record = JSON.parseObject(string, Feature.OrderedField);
        return record.getString("isDdl").equals(IsDdl) && record.getString("table").equals(TableName) && record.getString("type").equals(Type);
    }

    public String dataMap(String jsonvalue) throws Exception {
        StringBuilder fieldValue = new StringBuilder();
        JSONObject record = JSON.parseObject(jsonvalue, Feature.OrderedField);
        //获取最新的字段值
        JSONArray data = record.getJSONArray(Data);
        //遍历，字段值的JSON数组，只有一个元素
        for (int i = 0; i < data.size(); i++) {
            //获取data数组的所有字段
            JSONObject obj = data.getJSONObject(i);
            if (obj != null) {
                for (Map.Entry<String, Object> entry : obj.entrySet()) {
                    fieldValue.append(entry.getValue());
                    fieldValue.append(fieldDelimiter);
                }
            }
        }
        return fieldValue.toString();
    }
    public Tuple6<Integer,Integer,String,Integer,String,String> fieldMap(String datafield) throws Exception {
        Integer id= Integer.valueOf(datafield.split("[\\,]")[0]);
        Integer door_id= Integer.valueOf(datafield.split("[\\,]")[1]);
        String door_status= datafield.split("[\\,]")[2];
        Integer event_type= Integer.valueOf(datafield.split("[\\,]")[3]);
        String employee_sys_no= datafield.split("[\\,]")[4];
        String mend_date=datafield.split("[\\,]")[9];
        return new Tuple6<Integer,Integer,String,Integer,String, String>(id,door_id,door_status,event_type,employee_sys_no,mend_date);
    }

    public AccessEvent mapToAccessEvent(Tuple6<Integer,Integer,String,Integer,String,String> tuple6){
        int id=tuple6._1().intValue();
        int door_id=tuple6._2().intValue();
        String door_status=tuple6._3().toString();
        int event_type=tuple6._4().intValue();
        String employee_sys_no=tuple6._5().toString();
        String event_date=tuple6._6().toString();
        return new AccessEvent(id,door_id,door_status,event_type,employee_sys_no,event_date);
    }

}
