package cn.yuhui.hotel.service.impl;

import cn.yuhui.hotel.mapper.HotelMapper;
import cn.yuhui.hotel.pojo.Hotel;
import cn.yuhui.hotel.pojo.HotelDoc;
import cn.yuhui.hotel.pojo.PageResult;
import cn.yuhui.hotel.pojo.RequestParams;
import cn.yuhui.hotel.service.IHotelService;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class HotelService extends ServiceImpl<HotelMapper, Hotel> implements IHotelService {
    @Resource
    private RestHighLevelClient client;

    /**
     * 实现酒店的搜索和分页
     */
    @Override
    public PageResult search(RequestParams params) {
        try {
            SearchRequest request = new SearchRequest("hotel");
            // DSL
            // 获取搜索关键字
            String key = params.getKey();
            if (key == null || "".equals(key)) {
                // 若没有关键字，进行全文检索
                request.source().query(QueryBuilders.matchAllQuery());
            } else {
                // 否则，关键字检索
                request.source().query(QueryBuilders.matchQuery("all", key));
            }
            // 分页
            int page = params.getPage();
            int size = params.getSize();
            request.source().from((page - 1) * size).size(size);
            // 发送请求
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            // 解析响应
            return handleResponse(response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 结果解析
    private PageResult handleResponse(SearchResponse response) {
        // 解析响应
        SearchHits searchHits = response.getHits();
        // 总条数
        long total = searchHits.getTotalHits().value;
        // 文档数组
        SearchHit[] hits = searchHits.getHits();
        // 遍历
        List<HotelDoc> hotels = new ArrayList<>();
        for (SearchHit hit : hits) {
            // 获取文档source
            String json = hit.getSourceAsString();
            // 反序列化
            HotelDoc hotelDoc = JSON.parseObject(json, HotelDoc.class);
            // 放入集合
            hotels.add(hotelDoc);
        }
        // 封装返回
        return new PageResult(total, hotels);
    }
}
