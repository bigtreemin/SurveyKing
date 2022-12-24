package cn.surveyking.server;

import cn.surveyking.server.core.uitls.DatabaseInitHelper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class SurveyServerApplication {
	//chcp 65001   执行chcp 65001就可以把cmd的编码设置成uft-8了，这样就解决了乱码问题
	public static void main(String[] args) {

		//--server.port=1991 --spring.datasource.url=jdbc:mysql://localhost:3306/surveyking --spring.datasource.username=root --spring.datasource.password=123456
		 args = new String[] {"server.port=1991", "spring.datasource.url=jdbc:mysql://localhost:3306/sqlzhoumin", 
		"spring.datasource.username=aaa", "spring.datasource.password=bbb"};
		// // 快速执行数据库初始化操作
		if (args.length > 0) {
			DatabaseInitHelper.init(args);
		}

		SpringApplication.run(SurveyServerApplication.class, args);
	}

}