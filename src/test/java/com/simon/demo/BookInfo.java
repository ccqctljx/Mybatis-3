package com.simon.demo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @Author 陈辰强
 * @Date 2020/3/30 14:43
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class BookInfo implements Serializable {

    private Long bookId;
    private String bookName;
    private String bookAuthor;
    private String dataSource;

    public BookInfo(String bookName, String bookAuthor) {
        this.bookName = bookName;
        this.bookAuthor = bookAuthor;
    }
}
