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

    List<BookInfo> bookInfoList1 = sqlSession1.selectList("com.simon.demo.TestMapper.selectBookInfo");
    System.out.println(" sqlSession 1 query 1 ----------------------------- " + bookInfoList1);

    List<BookInfo> bookInfoList2 = sqlSession1.selectList("com.simon.demo.TestMapper.selectBookInfo");
    System.out.println("sqlSession 1 query 2 -----------------------------" + bookInfoList2);

    sqlSession1.commit();
    System.out.println("sqlSession 1 commit -----------------------------");

    List<BookInfo> bookInfoList3 = sqlSession2.selectList("com.simon.demo.TestMapper.selectBookInfo");
    System.out.println("sqlSession 2 query 1 ----------------------------- " + bookInfoList3);

    sqlSession1.insert("com.simon.demo.TestMapper.insertBookInfo");
    sqlSession1.update("com.simon.demo.TestMapper.updateBookInfo");
    sqlSession1.delete("com.simon.demo.TestMapper.deleteBookInfo");
  }
}
