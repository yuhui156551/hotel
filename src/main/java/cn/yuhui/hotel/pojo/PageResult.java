package cn.yuhui.hotel.pojo;

import lombok.Data;

import java.util.List;

/**
 * 响应结果实体类
 */
@Data
public class PageResult {
    private Long total;
    private List<HotelDoc> hotels;

    public PageResult() {
    }

    public PageResult(Long total, List<HotelDoc> hotels) {
        this.total = total;
        this.hotels = hotels;
    }
}