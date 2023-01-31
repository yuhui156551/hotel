package cn.yuhui.hotel.service;

import cn.yuhui.hotel.pojo.Hotel;
import cn.yuhui.hotel.pojo.PageResult;
import cn.yuhui.hotel.pojo.RequestParams;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface IHotelService extends IService<Hotel> {
    /**
     * 根据关键字搜索酒店信息
     */
    PageResult search(RequestParams params);

    /**
     * 聚合
     */
    Map<String, List<String>> getFilters(RequestParams params);
}
