package cn.yuhui.hotel.pojo;

import lombok.Data;

/**
 * 请求参数实体类
 */
@Data
public class RequestParams {
    private String key;
    private Integer page;
    private Integer size;
    private String sortBy;
    // 新增的过滤条件参数
    private String city;
    private String brand;
    private String starName;
    private Integer minPrice;
    private Integer maxPrice;
    // 当地的地理坐标
    private String location;
}