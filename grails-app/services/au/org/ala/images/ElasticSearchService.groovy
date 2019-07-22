package au.org.ala.images

import com.opencsv.CSVWriter
import grails.converters.JSON
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.search.SearchScrollRequest
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentType
import groovy.json.JsonOutput
import grails.web.servlet.mvc.GrailsParameterMap
import org.apache.http.HttpHost
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.SearchPhaseExecutionException
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.cluster.ClusterState
import org.elasticsearch.cluster.metadata.IndexMetaData
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.query.QueryStringQueryBuilder
import org.elasticsearch.search.Scroll
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.BucketOrder
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder
import org.elasticsearch.search.sort.SortOrder
import org.springframework.context.MessageSource

import javax.annotation.PreDestroy
import java.sql.Timestamp
import java.util.regex.Pattern
import javax.annotation.PostConstruct
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ElasticSearchService {

    def logService
    def grailsApplication
    def imageStoreService
    def collectoryService
    MessageSource messageSource

    private RestHighLevelClient client

    @PostConstruct
    def initialize() {
        client = new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("${grailsApplication.config.elasticsearch.host}", grailsApplication.config.elasticsearch.port1 as Integer, "${grailsApplication.config.elasticsearch.scheme}"),
                        new HttpHost("${grailsApplication.config.elasticsearch.host}", grailsApplication.config.elasticsearch.port2 as Integer, "${grailsApplication.config.elasticsearch.scheme}")
                )
        )
        initialiseIndex()
    }

    @PreDestroy
    def destroy() {
        if (client){
            try {
                client.close()
            } catch (Exception e){
                log.error("Error thrown trying to close down client", e)
            }
        }
    }

    def reinitialiseIndex() {
        try {
            def ct = new CodeTimer("Index deletion")
            def response = client.indices().delete(new DeleteIndexRequest(grailsApplication.config.elasticsearch.indexName), RequestOptions.DEFAULT)
            if (response.isAcknowledged()) {
                log.info "The index is removed"
            } else {
                log.error "The index could not be removed"
            }
            ct.stop(true)

        } catch (Exception ex) {
            log.error(ex.getMessage(), ex)
            // failed to delete index - maybe because it didn't exist?
        }
        initialiseIndex()
    }

    def indexImage(Image image) {

        if (!image){
            log.error("Supplied image was null")
            return
        }

        if (image.dateDeleted){
            log.debug("Supplied image is deleted")
            return
        }

        def ct = new CodeTimer("Index Image ${image.id}")
        // only add the fields that are searchable. They are marked with an annotation
        def fields = Image.class.declaredFields
        def data = [:]
        fields.each { field ->
            if (field.isAnnotationPresent(SearchableProperty)) {
                data[field.name] = image."${field.name}"
            }
        }

        if (image.recognisedLicense) {
            data.recognisedLicence = image.recognisedLicense.acronym
        } else {
            data.recognisedLicence = "unrecognised_licence"
        }

        indexImageInES(
                data.imageIdentifier,
                data.contentMD5Hash,
                data.contentSHA1Hash,
                data.mimeType,
                data.originalFilename,
                data.extension,
                data.dateUploaded,
                data.dateTaken,
                data.fileSize,
                data.height,
                data.width,
                data.zoomLevels,
                data.dataResourceUid,
                data.creator ,
                data.title,
                data.description,
                data.rights,
                data.rightsHolder,
                data.license,
                data.thumbHeight,
                data.thumbWidth,
                data.harvestable,
                data.recognisedLicence,
                data.occurrenceID
        )
        ct.stop(true)
    }

    def indexImageInES(
            imageIdentifier,
            contentMD5Hash,
            contentSHA1Hash,
            mimeType,
            originalFilename,
            extension,
            dateUploaded,
            dateTaken,
            fileSize,
            height,
            width,
            zoomLevels,
            dataResourceUid,
            creator,
            title,
            description,
            rights,
            rightsHolder,
            license,
            thumbHeight,
            thumbWidth,
            harvestable,
            recognisedLicence,
            occurrenceID
    ){
        def data = [
                imageIdentifier: imageIdentifier,
                contentMD5Hash: contentMD5Hash,
                contentSHA1Hash: contentSHA1Hash,
                format: mimeType,
                originalFilename: originalFilename,
                extension: extension,
                dateUploaded: dateUploaded,
                dateTaken: dateTaken,
                fileSize: fileSize,
                height: height,
                width: width,
                zoomLevels: zoomLevels,
                dataResourceUid: dataResourceUid,
                creator: creator,
                title: title,
                description:description,
                rights:rights,
                rightsHolder:rightsHolder,
                license:license,
                thumbHeight:thumbHeight,
                thumbWidth:thumbWidth,
                harvestable:harvestable,
                recognisedLicence: recognisedLicence,
                occurrenceID: occurrenceID
        ]

        addAdditionalIndexFields(data)

        def json = (data as JSON).toString()
        IndexRequest request = new IndexRequest(grailsApplication.config.elasticsearch.indexName)
        request.id(imageIdentifier)
        request.source(json, XContentType.JSON)
        IndexResponse indexResponse = client.index(request, RequestOptions.DEFAULT)
    }

    def bulkIndexImageInES(list){
        BulkRequest bulkRequest = new BulkRequest(grailsApplication.config.elasticsearch.indexName)
        list.each { data ->
            def indexRequest = new IndexRequest()
            addAdditionalIndexFields(data)
            def json = (data as JSON).toString()
            indexRequest.id(data.imageIdentifier)
            indexRequest.source(json, XContentType.JSON)
            bulkRequest.add(indexRequest)
        }
        bulkRequest.timeout(TimeValue.timeValueMinutes(5))
        BulkResponse indexResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT)
    }

    private def addAdditionalIndexFields(data){

        if(data.dateUploaded){

            if(data.dateUploaded instanceof java.util.Date){
                data.dateUploadedYearMonth = data.dateUploaded.format("yyyy-MM")
            } else {
                def newDate = Date.parseToStringDate(data.dateUploaded)
                if(newDate){
                    data.dateUploadedYearMonth =newDate.format("yyyy-MM")
                }
            }
        }

        data.recognisedLicence  = data.recognisedLicence ?: "unrecognised_licence"
        data.creator = data.creator ? data.creator.replaceAll("[\"|'&]", "") : "not_supplied"
        data.dataResourceUid = data.dataResourceUid ?: "no_dataresource"
        def imageSize = data.height.toInteger() * data.width.toInteger()
        if (imageSize < 100){
            data.imageSize = "less than 100"
        } else if (imageSize < 1000){
            data.imageSize = "less than 1k"
        } else if (imageSize < 10000){
            data.imageSize = "less than 10k"
        } else if (imageSize < 100000){
            data.imageSize = "less than 100k"
        } else if (imageSize < 1000000){
            data.imageSize = "less than 1m"
        } else {
            data.imageSize = (imageSize / 1000000).intValue() +"m"
        }
        data
    }

    def deleteImage(Image image) {
        if (image) {
            DeleteResponse response = client.delete(new DeleteRequest(grailsApplication.config.elasticsearch.indexName, image.id.toString()), RequestOptions.DEFAULT)
            log.info(response.status())
        }
    }

    QueryResults<Image> simpleImageSearch(List<SearchCriteria> searchCriteria, GrailsParameterMap params) {
        log.debug "search params: ${params}"
        SearchRequest request = buildSearchRequest(params, searchCriteria, grailsApplication.config.elasticsearch.indexName as String)
        SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT)
        def imageList = []
        if (searchResponse.hits) {
            searchResponse.hits.each { hit ->
               def image =  Image.findByImageIdentifier(hit.id)
               if(image) {
                   image.metadata = null
                   image.recognisedLicense = null
                   imageList << image
               }
            }
        }
        QueryResults<Image> qr = new QueryResults<Image>()
        qr.list = imageList
        qr.totalCount = searchResponse.hits.totalHits.value

        searchResponse.aggregations.each {
            def facet = [:]
            it.buckets.each { bucket ->
                facet[bucket.getKeyAsString()] = bucket.getDocCount()
            }
            qr.aggregations.put(it.name, facet)
        }

        qr
    }

    QueryResults<Image> simpleFacetSearch(List<SearchCriteria> searchCriteria, GrailsParameterMap params) {
        log.debug "search params: ${params}"
        SearchRequest request = buildFacetRequest(params, searchCriteria, params.facet, grailsApplication.config.elasticsearch.indexName as String)
        SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT)
        QueryResults<Image> qr = new QueryResults<Image>()

        searchResponse.aggregations.each {
            def facet = [:]
            it.buckets.each { bucket ->
                facet[bucket.getKeyAsString()] = bucket.getDocCount()
            }
            qr.aggregations.put(it.name, facet)
        }

        qr
    }


    void simpleImageDownload(List<SearchCriteria> searchCriteria, GrailsParameterMap params, OutputStream outputStream) {

        ZipOutputStream out = new ZipOutputStream(outputStream)
        ZipEntry e = new ZipEntry("images.csv")
        out.putNextEntry(e)

        def csvWriter = new CSVWriter(new OutputStreamWriter(out))
        def PAGE_SIZE = 10000
        params.offset = 0
        params.max = 1000
        def totalWritten = 0
        def fields = null

        final Scroll scroll = new Scroll(TimeValue.timeValueMinutes(1L));
        SearchRequest searchRequest = buildSearchRequest(params, searchCriteria, grailsApplication.config.elasticsearch.indexName as String)
        searchRequest.scroll(scroll)

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT)

        String scrollId = searchResponse.getScrollId()
        SearchHits hits = searchResponse.getHits()

        //Scroll until no hits are returned
        while (hits.getHits().length != 0) {
            for (SearchHit hit : hits.getHits()) {
                def map = hit.properties.sourceAsMap
                if (fields == null){
                    fields = ["imageURL"]
                    fields.addAll(map.keySet().sort())
                    csvWriter.writeNext(fields as String[])
                }
                def values = []

                fields.each {
                    if(it == "imageURL"){
                        values.add(imageStoreService.getImageUrl(map.get("imageIdentifier")))
                    } else {
                        values.add(map.get(it) ?: "")
                    }
                }
                csvWriter.writeNext(values as String[])
                totalWritten += 1
            }

            SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId)
            scrollRequest.scroll(TimeValue.timeValueSeconds(30))

            searchResponse = client.scroll(scrollRequest, RequestOptions.DEFAULT)
            scrollId = searchResponse.getScrollId()
            hits = searchResponse.getHits()
        }

        params.offset += PAGE_SIZE
        log.debug("Writing complete...." + totalWritten)
        csvWriter.flush()
        out.closeEntry()
        out.close()
    }

    /**
     * Execute the search using a map query.
     *
     * @param query
     * @param params
     * @return
     */
    QueryResults<Image> search(Map query, GrailsParameterMap params) {
        log.debug "search params: ${params}"
        SearchRequest request = buildSearchRequest(JsonOutput.toJson(query), params, grailsApplication.config.elasticsearch.indexName as String)
        SearchResponse searchResponse = client.search(request, RequestOptions.DEFAULT)
        def imageList = []
        if (searchResponse.hits) {
            searchResponse.hits.each { hit ->
                imageList << Image.findByImageIdentifier(hit.id)
            }
        }
        QueryResults<Image> qr = new QueryResults<Image>()
        qr.list = imageList
        qr.totalCount = searchResponse.hits.totalHits.value
        qr
    }

    /**
     * Build the search request object from query and params
     *
     * @param queryString
     * @param params
     * @param index index name
     * @param geoSearchCriteria geo search criteria.
     * @return SearchRequest
     */
    SearchRequest buildSearchRequest(Map params, List<SearchCriteria> criteriaList, String index) {

        SearchRequest request = new SearchRequest()
        request.searchType SearchType.DFS_QUERY_THEN_FETCH

        // set indices and types
        request.indices(index)
        def types = []
        if (params.types && params.types instanceof Collection<String>) {
            types = params.types
        }
        request.types(types as String[])

        //create query builder
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
        boolQueryBuilder.must(QueryBuilders.queryStringQuery(params.q ?: "*:*"))

        // Add FQ query filters
        def filterQueries = params.findAll { it.key == 'fq'}
        if (filterQueries) {
            filterQueries.each {

                if(it.value instanceof String[]){
                    it.value.each { filter ->
                        if(filter) {
                            def kv = filter.split(":")
                            boolQueryBuilder.must(QueryBuilders.termQuery(kv[0], kv[1]))
                        }
                    }
                } else {
                    if(it.value) {
                        def kv = it.value.split(":")
                        boolQueryBuilder.must(QueryBuilders.termQuery(kv[0], kv[1]))
                    }
                }
            }
        }

        //add search criteria
        boolQueryBuilder = createQueryFromCriteria(boolQueryBuilder, criteriaList)


        // set pagination stuff
        SearchSourceBuilder source = pagenateQuery(params).query(boolQueryBuilder)

        // request aggregations (facets)
        grailsApplication.config.facets.each { facet ->
            source.aggregation(AggregationBuilders.terms(facet as String).field(facet as String).size(10))
        }

        //ask for the total
        source.trackTotalHits(true)

        if (params.highlight) {
            source.highlight(new HighlightBuilder().preTags("<b>").postTags("</b>").field("_all", 60, 2))
        }

        request.source(source)

        request
    }


    /**
     * Build the search request object from query and params
     *
     * @param queryString
     * @param params
     * @param index index name
     * @param geoSearchCriteria geo search criteria.
     * @return SearchRequest
     */
    SearchRequest buildFacetRequest(Map params, List<SearchCriteria> criteriaList, String facet, String index) {

        SearchRequest request = new SearchRequest()
        request.searchType SearchType.DFS_QUERY_THEN_FETCH

        // set indices and types
        request.indices(index)
        def types = []
        if (params.types && params.types instanceof Collection<String>) {
            types = params.types
        }
        request.types(types as String[])

        //create query builder
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
        boolQueryBuilder.must(QueryBuilders.queryStringQuery(params.q ?: "*:*"))

        // Add FQ query filters
        def filterQueries = params.findAll { it.key == 'fq'}
        if (filterQueries) {
            filterQueries.each {

                if(it.value instanceof String[]){
                    it.value.each { filter ->
                        if(filter) {
                            def kv = filter.split(":")
                            boolQueryBuilder.must(QueryBuilders.termQuery(kv[0], kv[1]))
                        }
                    }
                } else {
                    if(it.value) {
                        def kv = it.value.split(":")
                        boolQueryBuilder.must(QueryBuilders.termQuery(kv[0], kv[1]))
                    }
                }
            }
        }

        //add search criteria
        boolQueryBuilder = createQueryFromCriteria(boolQueryBuilder, criteriaList)

        // set pagination stuff
        SearchSourceBuilder source = pagenateQuery(params).query(boolQueryBuilder)

        // request aggregations (facets)
        source.aggregation(AggregationBuilders.terms(facet as String).field(facet as String).size(10000).order(BucketOrder.key(true)))

        //ask for the total
        source.trackTotalHits(false)

        request.source(source)

        request
    }

    private SearchSourceBuilder pagenateQuery(Map params) {
        SearchSourceBuilder source = new SearchSourceBuilder()
        source.from(params.offset ? params.offset as int : 0)
        source.size(params.max ? params.max as int : 10)
        source.sort('dateUploaded', SortOrder.DESC)
        source
    }

    private def initialiseIndex() {
        try {

            boolean indexExists  = client.indices().exists(new org.elasticsearch.client.indices.GetIndexRequest(grailsApplication.config.elasticsearch.indexName), RequestOptions.DEFAULT)
            if (!indexExists){
                CreateIndexRequest request = new CreateIndexRequest(grailsApplication.config.elasticsearch.indexName)
                CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT)
                if (createIndexResponse.isAcknowledged()) {
                    log.info "Successfully created index and mappings for images"
                } else {
                    log.info "UN-Successfully created index and mappings for images"
                }

                PutMappingRequest putMappingRequest = new PutMappingRequest(grailsApplication.config.elasticsearch.indexName)
                putMappingRequest.type("images")
                putMappingRequest.source(
                        """{
                                  "properties": {
                                    "dateUploaded": {
                                      "type": "date"
                                    },
                                    "dataResourceUid": {
                                      "type": "keyword"
                                    },               
                                    "license": {
                                      "type": "keyword"
                                    },  
                                    "recognisedLicence": {
                                      "type": "keyword"
                                    },    
                                    "imageSize":{
                                       "type": "keyword"
                                    },
                                    "dateUploadedYearMonth":{
                                       "type": "keyword"
                                    }, 
                                    "format":{
                                       "type": "keyword"
                                    },                                                                         
                                    "createdYear":{
                                       "type": "keyword"
                                    },                                                                     
                                    "creator": {
                                      "type": "keyword"
                                    }                                                                                             
                                  }
                                }""",
                        XContentType.JSON)
                def resp = client.indices().putMapping(putMappingRequest, RequestOptions.DEFAULT)
            } else {
                log.info "Index already exists"
            }

        } catch (Exception e) {
            log.error ("Error creating index for images: ${e.message}", e)
        }
    }

    /**
     * Create a boolean elastic search query builder from the supplied criteria.
     * @param criteriaList
     * @return
     */
    private BoolQueryBuilder createQueryFromCriteria(BoolQueryBuilder boolQueryBuilder, List<SearchCriteria> criteriaList) {

        def metaDataPattern = Pattern.compile("^(.*)[:](.*)\$")

        // split out by criteria type
        def criteriaMap = criteriaList.groupBy { it.criteriaDefinition.type }

        def list = criteriaMap[CriteriaType.ImageProperty]
        if (list) {
            ESSearchCriteriaUtils.buildCriteria(boolQueryBuilder, list)
        }

        list = criteriaMap[CriteriaType.ImageMetadata]
        if (list) {
            for (int i = 0; i < list.size(); ++i) {
                def criteria = list[i]
                // need to split the metadata name out of the value...
                def matcher = metaDataPattern.matcher(criteria.value)
                if (matcher.matches()) {
                    def term = matcher.group(2)?.replaceAll('\\*', '%')
                    term = term.replaceAll(":", "\\:")

                    boolQueryBuilder.must(QueryBuilders.queryFilter(QueryBuilders.queryStringQuery("${matcher.group(1)}:${term}")))
                }
            }
        }

        boolQueryBuilder
    }

    QueryResults<Image> searchByMetadata(String key, List<String> values, GrailsParameterMap params) {
        def queryString = values.collect { key.toLowerCase() + ":\"" + it + "\""}.join(" OR ")
        QueryStringQueryBuilder builder = QueryBuilders.queryStringQuery(queryString)
        builder.defaultField("content")
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        searchSourceBuilder.query(builder)
        return executeSearch(searchSourceBuilder, params)
    }

    private QueryResults<Image> executeSearch(GrailsParameterMap params) {

        try {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()

            QueryBuilder queryBuilder = QueryBuilders.queryStringQuery(params.q)

            def filterQueries = params.findAll { it.key == 'fq'}

            def filters = []

            if (filterQueries) {
                filterQueries.each {

                    if(it.value instanceof String[]){
                        it.value.each { filter ->
                            def kv = filter.split(":")
                            filters << QueryBuilders.termQuery(kv[0], kv[1])
                        }
                    } else {
                        def kv = it.value.split(":")
                        filters << QueryBuilders.termQuery(kv[0], kv[1])
                    }
                }
            }

            if (filters) {
                BoolQueryBuilder builder = QueryBuilders.boolQuery()
                filters.each {
                    builder.must(it)
                }
                queryBuilder = builder.must(queryBuilder) //QueryBuilders.termQuery(qsQuery). builder)
            }

            if (params?.offset) {
                searchSourceBuilder.from(params.int("offset"))
            }

            if (params?.max) {
                searchSourceBuilder.size(params.int("max"))
            } else {
                searchSourceBuilder.size(Integer.MAX_VALUE) // probably way too many!
            }

            if (params?.sort) {
                def order = params?.order == "asc" ? SortOrder.ASC : SortOrder.DESC
                searchSourceBuilder.sort(params.sort as String, order)
            }

            // aggregations = facets
            grailsApplication.config.facets.each { facet ->
                searchSourceBuilder.aggregation(AggregationBuilders.terms(facet as String).field(facet as String))
            }

            def ct = new CodeTimer("Index search")
            SearchRequest searchRequest = new SearchRequest()
            searchRequest.indices(grailsApplication.config.elasticsearch.indexName as String)
            searchRequest.source(searchSourceBuilder)
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT)

            ct.stop(true)

            ct = new CodeTimer("Object retrieval (${searchResponse.hits.hits.length} of ${searchResponse.hits.totalHits} hits)")
            def imageList = []
            def aggregations = [:]
            if (searchResponse.hits) {
                searchResponse.hits.each { hit ->
                    imageList << Image.get(hit.id.toLong())
                }
                searchResponse.aggregations.each {
                    def facet = [:]
                    it.buckets.each { bucket ->
                        facet[bucket.getKeyAsString()] = bucket.getDocCount()
                    }
                    aggregations.put(it.name, facet)
                }
            }
            ct.stop(true)

            return new QueryResults<Image>(list: imageList, aggregations:aggregations, totalCount: searchResponse?.hits?.totalHits.value ?: 0)
        } catch (SearchPhaseExecutionException e) {
            log.warn(".SearchPhaseExecutionException thrown - this is expected behaviour for a new empty system.")
            return new QueryResults<Image>(list: [], totalCount: 0)
        } catch (Exception e) {
            e.printStackTrace()
            log.warn("Exception thrown - this is expected behaviour for a new empty system.")
            return new QueryResults<Image>(list: [], totalCount: 0)
        }
    }

    def getMetadataKeys() {
        ClusterState cs = client.admin().cluster().prepareState().execute().actionGet().getState();
        IndexMetaData imd = cs.getMetaData().index(grailsApplication.config.elasticsearch.indexName as String)
        Map mdd = imd.mapping("image").sourceAsMap()
        Map metadata = mdd?.properties?.metadata?.properties
        def names = []
        if (metadata) {
            names = metadata.collect { it.key }
        }
        names
    }
}
