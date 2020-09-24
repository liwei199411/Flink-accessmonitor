[TOC]

## 1.需求说明

***

**需求**：

- 人员刷卡离厂之后，应设置离厂超时时间，以此来规范员人员的离厂时间
- 员工离厂超时90分钟以上，仍未入场，则输出预警信息（发送邮件或者短信）

## 2.实现思路

***

目前有一张刷卡记录表，实时更新，它会记录人员出入厂刷卡信息。下面是Flink CEP 中用到的字段：

`id`，自增主键

 `door_id`，门禁ID（关联门信息表：`mj_door`）

`door_status`，刷卡出入时 门的状态，`0`代表关，`1`代表进开门，`2`代表出开门

`event_type`，事件类型

`employee_sys_no`，人员ID

`event_date`，事件事件

```java
/**
实现思路：
	利用Flink CEP进行时间流的模式匹配，并设定超时时间（90minutes）
具体如下：
	按照某个人员ID聚合设定时间内的刷卡进出事件：
	人员进厂，进开门，door_status=1
	人员中途离厂，出开门，door_status=2,从这时开始触发 Flink CEP 匹配规则
	员工再次进厂，进开门，door_status=1，/如果距离上次刷卡出厂时间超过90minutes仍未检测到刷卡入厂，即door_status仍是2，则进行超时预警。
*/
```

```java
/**
Flink CEP复杂事件处理：
	定义一个Pattern匹配出Pattern里 in_door与 out_door事件时间间隔>90 minutes的事件
	对这些输出进行预警，获取超时未匹配的事件，得到人员ID
*/
```

- 1.IN：DataSource->DataStream->Transformation->DataStream->keyBy->KeyedStream

- 2.Pattern：Pattern.begin.where.next.where...within(Time windowTime)

- 3.PatternStream：CEP.pattern(KeyedStream,Pattern)

- 4.OutputTag:new OutTag(...)

- 5.SingleOutputStreamOperator:

  PatternStream.flatSelect(OutputTag,PatternFlatTimeoutFunction,PatternFlatSelectFunction)

- 6.TimeOutDataStream:SingleOutputStreamOperator.getSideOutput(OutputTag)

- 7.OUT:DataStream->Transformations->DataStream->DataSink

## 3.案例实现Demo

### 3.1 定义一个刷卡出入事件类

```java
package com.events;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.Objects;

/**
 *  AccessEvent 刷卡访问事件的实体类对象
 * */
//@Data
//@AllArgsConstructor
@NoArgsConstructor
public class AccessEvent implements Serializable {
    public Integer id;
    public Integer door_id;
    public String door_status;
    public Integer event_type;
    public String employee_sys_no;
    public String datetime;

    public AccessEvent(AccessEvent indoor) {
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getDoor_id() {
        return door_id;
    }

    public void setDoor_id(int door_id) {
        this.door_id = door_id;
    }

    public String getDoor_status() {
        return door_status;
    }

    public void setDoor_status(String door_status) {
        this.door_status = door_status;
    }

    public int getEvent_type() {
        return event_type;
    }

    public void setEvent_type(int event_type) {
        this.event_type = event_type;
    }

    public String getEmployee_sys_no() {
        return employee_sys_no;
    }

    public void setEmployee_sys_no(String employee_sys_no) {
        this.employee_sys_no = employee_sys_no;
    }

    public String getDatetime() {
        return datetime;
    }

    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }

    public AccessEvent(int id, int door_id, String door_status, int event_type, String employee_sys_no, String datetime) {
        this.id = id;
        this.door_id = door_id;
        this.door_status = door_status;
        this.event_type = event_type;
        this.employee_sys_no = employee_sys_no;
        this.datetime = datetime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccessEvent that = (AccessEvent) o;
        return id == that.id &&
                door_id == that.door_id &&
                door_status == that.door_status &&
                event_type == that.event_type &&
                employee_sys_no == that.employee_sys_no &&
                Objects.equals(datetime, that.datetime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, door_id, door_status, event_type, employee_sys_no, datetime);
    }
    @Override
    public String toString() {
        return "AccessEvent{" +
                "id=" + id +
                ", door_id=" + door_id +
                ", door_status=" + door_status +
                ", event_type=" + event_type +
                ", employee_sys_no=" + employee_sys_no +
                ", datetime='" + datetime + '\'' +
                '}';
    }
}
```

### 3.2 定义一个事件模式

```java
/**
定义一个事件模式（Pattern）
*/
Pattern<AccessEvent,AccessEvent> warningPattern=Pattern.<AccessEvent>begin("outdoor")
    .where(new SimpleCondition<AccessEvent>() {
        private static final long serialVersionUID = -6847788055093903603L;
        @Override
        public boolean filter(AccessEvent accessEvent) throws Exception {
            return accessEvent.getDoor_status().equals("2");
        }
     })
    .next("indoor").where(new SimpleCondition<AccessEvent>() {
        @Override
        public boolean filter(AccessEvent accessEvent) throws Exception {
            return accessEvent.getDoor_status().equals("1");
      }
     })
    .within(Time.seconds(10)).times(1);
```

### 3.3 Build pattern Stream，模式匹配输出

```java
/**
 * 模式匹配输出
 * */
PatternStream<AccessEvent> accessEventPatternStream=CEP.pattern(dataStreamKeyBy,warningPattern);
```

### 3.4 Use side output get timeout stream，获取超时输出流

```java
OutputTag<AccessEvent> outputTag=new OutputTag<AccessEvent>("timedout"){
	private static final long serialVersionUID = 773503794597666247L;
};
SingleOutputStreamOperator<AccessEvent> timeout=accessEventPatternStream.flatSelect(
		outputTag,
		new AccessTimedOut(),
		new FlatSelect()
);
/**
 * 把超时的事件收集起来
 * */
public static class AccessTimedOut implements PatternFlatTimeoutFunction<AccessEvent,AccessEvent> {
	private static final long serialVersionUID = -4214641891396057732L;
	@Override
	public void timeout(Map<String, List<AccessEvent>> pattern, long timeStamp, Collector<AccessEvent> out) throws Exception {
		if (null!=pattern.get("outdoor")){
			for (AccessEvent accessEvent:pattern.get("outdoor")){
				System.out.println("timeout outdoor:"+accessEvent.getEmployee_sys_no());
				out.collect(accessEvent);
			}
		}
		//因为indoor 超时了，还没有收到indoor,所以这里是拿不到 indoor 的
		System.out.println("timeout end"+pattern.get("indoor"));
	}
}
```



