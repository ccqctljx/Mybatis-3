package com.simon.demo;

import lombok.Data;
import lombok.ToString;

import java.util.Date;

/**
 * @Author 陈辰强
 * @Date 2020/3/10 20:15
 */
@Data
@ToString
public class EagleMenu {
  private Integer MENU_ID;
  private Integer PARENT_ID;
  private String PARENT_IDS;
  private String SORT;
  private String MENU_NAME;
  private String MENU_HREF;
  private String MENU_ICON;
  private String PERMISSION;
  private String USEABLE;
  private String CREATE_BY;
  private Date CREATE_DATE;
  private String UPDATE_BY;
  private Date UPDATE_DATE;
  private String REMARKS;

}
