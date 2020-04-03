package com.simon.demo;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @Author 陈辰强
 * @Date 2020/3/10 20:06
 */
public class Demo {
  public static void main(String[] args) throws IOException {
    String resource = "mybatis/mybatis-config.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
    SqlSession sqlSession1 = sqlSessionFactory.openSession();
    SqlSession sqlSession2 = sqlSessionFactory.openSession();

    List<BookInfo> bookInfo1 = sqlSession1.selectList("com.simon.demo.TestMapper.selectBookInfo");
//    sqlSession1.close();
    sqlSession1.commit();
    System.out.println(bookInfo1);

    List<BookInfo> bookInfo2 = sqlSession2.selectList("com.simon.demo.TestMapper.selectBookInfo");
    System.out.println(bookInfo2);
    sqlSession2.close();

  }
}
