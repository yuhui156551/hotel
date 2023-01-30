package cn.yuhui.hotel.service;

import cn.yuhui.hotel.pojo.Hotel;
import cn.yuhui.hotel.pojo.PageResult;
import cn.yuhui.hotel.pojo.RequestParams;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IHotelService extends IService<Hotel> {
    /**
     * 根据关键字搜索酒店信息
     * @param params 请求参数对象，包含用户输入的关键字
     * @return 酒店文档列表
     */
    PageResult search(RequestParams params);
}
