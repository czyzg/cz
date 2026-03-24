package com.cz.Milvus;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import okhttp3.*;
import java.io.IOException;
import java.util.*;

public class MilvusInsertDemo {

    private static final String SILICONFLOW_API_KEY = "sk-wbazldxfmzsixfgswakburovacequyabfljwonjpmdghfalc";
    private static final String EMBEDDING_URL = "https://api.siliconflow.cn/v1/embeddings";
    private static final String EMBEDDING_MODEL = "Qwen/Qwen3-Embedding-8B";
    private static final Gson GSON = new Gson();
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final String MILVUS_URI = "http://192.168.70.108:19530";

    public static void main(String[] args) throws IOException {
        // 连接 Milvus（省略，同上一节）
        MilvusClientV2 client = connectMilvus();

        // 模拟电商客服知识库的 chunk 数据
//        List<String> chunkTexts = List.of(
//                "退货政策：自签收之日起 7 天内，商品未拆封、不影响二次销售的情况下，支持无理由退货。退货运费由买家承担，质量问题除外。",
//                "退货政策：生鲜食品、定制商品、贴身衣物等特殊商品不支持无理由退货。如有质量问题，请在签收后 48 小时内联系客服并提供照片凭证。",
//                "物流规则：普通商品下单后 48 小时内发货，预售商品以商品详情页标注的发货时间为准。偏远地区（新疆、西藏、青海等）可能需要额外 2~3 天。",
//                "物流规则：支持顺丰、中通、圆通、韵达等主流快递。默认使用中通快递，如需指定快递公司，请在下单时备注，可能产生额外运费。",
//                "促销活动：2026 年春节大促，全场满 300 减 50，满 500 减 100。活动时间：2026 年 1 月 20 日至 2 月 5 日。优惠券不可叠加使用。"
//        );
//        List<String> docIds = List.of("doc_return_001", "doc_return_001", "doc_logistics_001", "doc_logistics_001", "doc_promo_001");
//        List<String> categories = List.of("return_policy", "return_policy", "logistics", "logistics", "promotion");
//
//        // 调用 Embedding API 生成向量
//        List<List<Float>> vectors = getEmbeddings(chunkTexts);
//
//        // 组装插入数据
//        List<JsonObject> rows = new ArrayList<>();
//        for (int i = 0; i < chunkTexts.size(); i++) {
//            JsonObject row = new JsonObject();
//            row.addProperty("chunk_text", chunkTexts.get(i));
//            row.addProperty("doc_id", docIds.get(i));
//            row.addProperty("category", categories.get(i));
//            row.add("vector", GSON.toJsonTree(vectors.get(i)));
//            rows.add(row);
//        }
//
//        // 插入 Milvus
//        InsertReq insertReq = InsertReq.builder()
//                .collectionName("customer_service_chunks")
//                .data(rows)
//                .build();
//        InsertResp insertResp = client.insert(insertReq);
//        System.out.println("插入成功，数量：" + insertResp.getInsertCnt());

        IndexParam vectorIndex = IndexParam.builder()
                .fieldName("vector")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)  // 余弦相似度
                .extraParams(Map.of(
                        "M", 16,              // 每个向量的最大连接数
                        "efConstruction", 256 // 建索引时的搜索宽度
                ))
                .build();

// 为 category 标量字段创建索引（加速过滤查询）
        IndexParam categoryIndex = IndexParam.builder()
                .fieldName("category")
                .indexType(IndexParam.IndexType.TRIE)  // 字符串类型用 Trie 索引
                .build();

        CreateIndexReq createIndexReq = CreateIndexReq.builder()
                .collectionName("customer_service_chunks")
                .indexParams(List.of(vectorIndex, categoryIndex))
                .build();
        client.createIndex(createIndexReq);
        System.out.println("索引创建成功");
        client.loadCollection(LoadCollectionReq.builder()
                .collectionName("customer_service_chunks")
                .build());
        System.out.println("Collection 已加载到内存");
        String query = "买了东西不想要了怎么退货？";

// 把问题向量化（复用前面的 getEmbeddings 方法）
        List<List<Float>> queryVectors = getEmbeddings(List.of(query));

        List<BaseVector> milvusQueryVectors = queryVectors.stream()
                .map(FloatVec::new)   // FloatVec(List<Float>)
                .collect(java.util.stream.Collectors.toList());

// 执行向量检索
        SearchReq searchReq = SearchReq.builder()
                .collectionName("customer_service_chunks")
                .data(milvusQueryVectors)           // 查询向量
                .topK(3)                      // 返回最相似的 3 个结果
                .outputFields(List.of("chunk_text", "doc_id", "category"))  // 需要返回的字段
                .annsField("vector")          // 指定在哪个向量字段上检索
                .searchParams(Map.of("ef", 128))  // HNSW 检索时的搜索宽度
                .build();

        SearchResp searchResp = client.search(searchReq);

// 输出检索结果
        List<List<SearchResp.SearchResult>> results = searchResp.getSearchResults();
        for (List<SearchResp.SearchResult> resultList : results) {
            System.out.println("=== 检索结果 ===");
            for (int i = 0; i < resultList.size(); i++) {
                SearchResp.SearchResult result = resultList.get(i);
                System.out.println("Top-" + (i + 1) + "：");
                System.out.println("  相似度分数：" + result.getScore());
                System.out.println("  分类：" + result.getEntity().get("category"));
                System.out.println("  文档ID：" + result.getEntity().get("doc_id"));
                System.out.println("  内容：" + result.getEntity().get("chunk_text"));
                System.out.println();
            }
        }

    }

    /**
     * 连接到 Milvus 数据库
     */
    private static MilvusClientV2 connectMilvus() {
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(MILVUS_URI)
                .build();
        MilvusClientV2 client = new MilvusClientV2(connectConfig);
        System.out.println("已连接到 Milvus: " + MILVUS_URI);
        return client;
    }

    /**
     * 调用 SiliconFlow Embedding API，批量生成向量
     */
    private static List<List<Float>> getEmbeddings(List<String> texts) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", EMBEDDING_MODEL);
        requestBody.add("input", GSON.toJsonTree(texts));

        Request request = new Request.Builder()
                .url(EMBEDDING_URL)
                .addHeader("Authorization", "Bearer " + SILICONFLOW_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(GSON.toJson(requestBody),
                        MediaType.parse("application/json")))
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API 请求失败: " + response.code() + " " + response.body().string());
            }
            
            String body = response.body().string();
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            JsonArray dataArray = json.getAsJsonArray("data");

            List<List<Float>> vectors = new ArrayList<>();
            for (int i = 0; i < dataArray.size(); i++) {
                JsonObject dataObj = dataArray.get(i).getAsJsonObject();
                JsonArray embeddingArray = dataObj.getAsJsonArray("embedding");
                List<Float> vector = new ArrayList<>();
                for (int j = 0; j < embeddingArray.size(); j++) {
                    vector.add(embeddingArray.get(j).getAsFloat());
                }
                vectors.add(vector);
            }
            return vectors;
        }

    }
}
