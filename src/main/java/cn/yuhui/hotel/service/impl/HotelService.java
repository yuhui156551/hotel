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
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            // boolean查询
            buildBasicQuery(params, request);
            // 分页
            int page = params.getPage();
            int size = params.getSize();
            request.source().from((page - 1) * size).size(size);
            // 根据地理位置距离排序
            String location = params.getLocation();
            if(location != null && !"".equals(location)){
                request.source().sort(SortBuilders
                        .geoDistanceSort("location", new GeoPoint(location))
                        .order(SortOrder.ASC)
                        .unit(DistanceUnit.KILOMETERS));
            }
            // 发送请求
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            // 解析响应
            return handleResponse(response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, List<String>> getFilters(RequestParams params) {
        try {
            SearchRequest request = new SearchRequest("hotel");
            buildBasicQuery(params, request);
            request.source().size(0);
            // 聚合
            buildAggregation(request);
            // 发送请求
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            // 解析经过过滤的结果
            Aggregations aggregations = response.getAggregations();
            Map<String, List<String>> result = new HashMap<>();
            // 根据名称，获取结果
            List<String> brandList = getAggByName(aggregations, "brandAgg");
            result.put("品牌", brandList);
            List<String> cityList = getAggByName(aggregations, "cityAgg");
            result.put("城市", cityList);
            List<String> starList = getAggByName(aggregations, "starAgg");
            result.put("星级", starList);
            // 返回结果
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> getSuggestions(String prefix) {
        try {
            SearchRequest request = new SearchRequest("hotel");
            // DSL
            request.source().suggest(new SuggestBuilder().addSuggestion(
                    "suggestions",
                    SuggestBuilders.completionSuggestion("suggestion")// 补全字段
                            .prefix(prefix)// 关键字
                            .skipDuplicates(true)
                            .size(10)
            ));
            // 发起请求
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            // 解析结果
            Suggest suggest = response.getSuggest();
            // 根据补全查询名称，获取补全结果
            CompletionSuggestion suggestions = suggest.getSuggestion("suggestions");
            // 获取options
            List<CompletionSuggestion.Entry.Option> options = suggestions.getOptions();
            // 遍历
            List<String> list = new ArrayList<>(options.size());
            for (CompletionSuggestion.Entry.Option option : options) {
                // 获取一个option的text，也就是补全的词条
                String text = option.getText().toString();
                list.add(text);
            }
            return list;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 根据名称获取对应结果
     */
    private List<String> getAggByName(Aggregations aggregations, String aggName) {
        // 根据聚合名称获取聚合结果
        Terms brandTerms = aggregations.get(aggName);
        // 获取buckets
        List<? extends Terms.Bucket> buckets = brandTerms.getBuckets();
        // 遍历
        List<String> brandList = new ArrayList<>();
        for (Terms.Bucket bucket : buckets) {
            // 获取key，也就是品牌等信息
            String key = bucket.getKeyAsString();
            brandList.add(key);
        }
        return brandList;
    }

    /**
     * 限定范围的聚合
     */
    private void buildAggregation(SearchRequest request) {
        // 品牌分组
        request.source().aggregation(AggregationBuilders
                .terms("brandAgg")
                .field("brand")
                .size(100));
        // 城市分组
        request.source().aggregation(AggregationBuilders
                .terms("cityAgg")
                .field("city")
                .size(100));
        // 星级分组
        request.source().aggregation(AggregationBuilders
                .terms("starAgg")
                .field("starName")
                .size(100));
    }

    /**
     * 解析结果
     */
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
            // 获取排序值
            Object[] sortValues = hit.getSortValues();
            if(sortValues.length > 0){
                hotelDoc.setDistance(sortValues[0]);
            }
            // 放入集合
            hotels.add(hotelDoc);
        }
        // 封装返回
        return new PageResult(total, hotels);
    }

    /**
     * query
     */
    private void buildBasicQuery(RequestParams params, SearchRequest request) {
        // 构建BooleanQuery
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        // 关键字搜索
        String key = params.getKey();
        if (key == null || "".equals(key)) {
            boolQuery.must(QueryBuilders.matchAllQuery());
        } else {
            boolQuery.must(QueryBuilders.matchQuery("all", key));
        }
        // 城市条件
        if (params.getCity() != null && !"".equals(params.getCity())) {
            boolQuery.filter(QueryBuilders.termQuery("city", params.getCity()));
        }
        // 品牌条件
        if (params.getBrand() != null && !"".equals(params.getBrand())) {
            boolQuery.filter(QueryBuilders.termQuery("brand", params.getBrand()));
        }
        // 星级条件
        if (params.getStarName() != null && !"".equals(params.getStarName())) {
            boolQuery.filter(QueryBuilders.termQuery("starName", params.getStarName()));
        }
        // 价格
        if (params.getMinPrice() != null && params.getMaxPrice() != null) {
            boolQuery.filter(QueryBuilders
                    .rangeQuery("price")
                    .gte(params.getMinPrice())
                    .lte(params.getMaxPrice())
            );
        }
        // 算分控制
        /**
         * 添加标记：
         * POST /hotel/_update/2056126831
         * {
         *     "doc": {
         *         "isAD": true
         *     }
         * }
         * POST /hotel/_update/1989806195
         * {
         *     "doc": {
         *         "isAD": true
         *     }
         * }
         * POST /hotel/_update/2056105938
         * {
         *     "doc": {
         *         "isAD": true
         *     }
         * }
         */
        FunctionScoreQueryBuilder functionScoreQuery =
                QueryBuilders.functionScoreQuery(
                        // 原始查询，相关性算分的查询
                        boolQuery,
                        // function score的数组
                        new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                                // 其中的一个function score 元素
                                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                                        // 过滤条件
                                        QueryBuilders.termQuery("isAD", true),
                                        // 算分函数
                                        ScoreFunctionBuilders.weightFactorFunction(10)
                                )
                        });
        request.source().query(functionScoreQuery);
        // 放入source
//        request.source().query(boolQuery);
    }
}
