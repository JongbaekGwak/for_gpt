package com.example.no_name.config;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

public class DatasourceConfig {
    @Configuration
    @MapperScan(basePackages = "com.example.myapp.mapper")
    public class DataSourceConfig {

        @Value("${spring.datasource.driver-class-name}")
        private String driverClassName;

        @Value("${spring.datasource.url}")
        private String url;

        @Value("${spring.datasource.username}")
        private String username;

        @Value("${spring.datasource.password}")
        private String password;

        @Bean
        public DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName(driverClassName);
            dataSource.setUrl(url);
            dataSource.setUsername(username);
            dataSource.setPassword(password);
            return dataSource;
        }

        @Bean
        public SqlSessionFactoryBean sqlSessionFactoryBean(DataSource dataSource, ApplicationContext applicationContext) throws Exception {
            SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
            //SqlExecutorPlugin sqlExecutorPlugin = new SqlExecutorPlugin(applicationContext);
            //sqlSessionFactoryBean.setPlugins(new Interceptor[]{sqlExecutorPlugin});
            sqlSessionFactoryBean.setDataSource(dataSource);
            sqlSessionFactoryBean.setMapperLocations(applicationContext.getResources("classpath:sql/**/*.xml"));
            sqlSessionFactoryBean.getObject().getConfiguration().setMapUnderscoreToCamelCase(true);
            return sqlSessionFactoryBean;
        }

        @Bean
        public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }
    }

}