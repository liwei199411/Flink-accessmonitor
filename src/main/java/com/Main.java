package com;
import com.events.AccessEvent;
import com.utils.JsonFilter;
import com.utils.KafkaConfigUtil;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.cep.*;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.functions.IngestionTimeExtractor;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple6;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Main {
private static Logger log = LoggerFactory.getLogger(Main.class);

public static void main(String[] args) throws Exception {
	/**
	 * Flink 配置
	 * */
	StreamExecutionEnvironment env=StreamExecutionEnvironment.getExecutionEnvironment();
//        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

	env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);//设置事件时间
	env.enableCheckpointing(1000);////非常关键，一定要设置启动检查点

	env.getCheckpointConfig().enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
	env.getConfig().disableSysoutLogging();//设置此可以屏蔽掉日记打印情况
	env.getConfig().setRestartStrategy(RestartStrategies.fixedDelayRestart(5, 10000));

	// 不使用POJO的时间
	final AssignerWithPeriodicWatermarks extractor =new IngestionTimeExtractor<AccessEvent>();

	/**
	 *   Kafka配置
	 * */
	Properties properties = KafkaConfigUtil.buildKafkaProps();//kafka参数配置
	FlinkKafkaConsumer<String> consumer = new FlinkKafkaConsumer<>(KafkaConfigUtil.topic, new SimpleStringSchema(), properties);

	DataStream<AccessEvent> accessEventDataStream=env.addSource(consumer)
			.filter(new FilterFunction<String>() {
				@Override
				public boolean filter(String jsonVal) throws Exception {
					return new JsonFilter().getJsonFilter(jsonVal);
				}
			}).map(new MapFunction<String, String>() {
				@Override
				public String map(String jsonvalue) throws Exception {
					return new JsonFilter().dataMap(jsonvalue);
				}
			}).map(new MapFunction<String, Tuple6<Integer,Integer,String,Integer,String,String>>() {
				//将json解析为Tuple6类型
				@Override
				public Tuple6<Integer,Integer,String,Integer,String,String> map(String dataField) throws Exception {
					return new JsonFilter().fieldMap(dataField);
				}
			}).map(new MapFunction<Tuple6<Integer, Integer, String, Integer, String, String>, AccessEvent>() {
				//将Tuple6类型解析为AccessEvent
				@Override
				public AccessEvent map(Tuple6<Integer, Integer, String, Integer, String, String> tuple6) throws Exception {
					return new JsonFilter().mapToAccessEvent(tuple6);
				}
			}).assignTimestampsAndWatermarks(extractor);
//        accessEventDataStream.print();

	//根据 employee_sys_no 进行分组
//        DataStream<AccessEvent> dataStreamKeyBy =accessEventDataStream.keyBy("employee_sys_no");
	DataStream<AccessEvent> dataStreamKeyBy=accessEventDataStream.keyBy(AccessEvent::getEmployee_sys_no);
	/**
	 * 定义一个事件模式（Pattern）
	 * */
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
	/**
	 * 模式匹配输出
	 * */
	PatternStream<AccessEvent> accessEventPatternStream=CEP.pattern(dataStreamKeyBy,warningPattern);
	/**
	 * 匹配事件处理
	 * */
//测试 最简单的accessEventPatternStream.select,测试ok
//        SingleOutputStreamOperator<AccessEvent> singleOutputStreamOperator=accessEventPatternStream.select(new PatternSelectFunction<AccessEvent,AccessEvent>() {
//            @Override
//            public AccessEvent select(Map<String, List<AccessEvent>> map) throws Exception {
//                return (AccessEvent) map.get("indoor").get(0);
//            }
//        });
//        singleOutputStreamOperator.print();

	OutputTag<AccessEvent> outputTag=new OutputTag<AccessEvent>("timedout"){
		private static final long serialVersionUID = 773503794597666247L;
	};
	SingleOutputStreamOperator<AccessEvent> timeout=accessEventPatternStream.flatSelect(
			outputTag,
			new AccessTimedOut(),
			new FlatSelect()
	);
			//打印输出超时的AccessEvent
	timeout.getSideOutput(outputTag).print();
	timeout.print();

	env.execute(Main.class.getSimpleName());
}
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

/**
 * 未超时的事件
 * */
public static class  FlatSelect implements PatternFlatSelectFunction<AccessEvent,AccessEvent> {
	private static final long serialVersionUID = -3029589950677623844L;
	@Override
	public void flatSelect(Map<String, List<AccessEvent>> pattern, Collector<AccessEvent> collector) throws Exception {
		System.out.println("flatSelect"+pattern.get("indoor"));
		collector.collect(new AccessEvent());
	}
}
}
