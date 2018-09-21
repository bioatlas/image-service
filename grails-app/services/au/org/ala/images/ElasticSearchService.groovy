package au.org.ala.images

import grails.converters.JSON
import grails.transaction.NotTransactional
import groovy.json.JsonSlurper
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.SearchPhaseExecutionException
import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.client.Client
import org.elasticsearch.cluster.ClusterState
import org.elasticsearch.cluster.metadata.IndexMetaData
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.index.query.FilterBuilder
import org.elasticsearch.index.query.FilterBuilders
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.query.QueryStringQueryBuilder
import org.elasticsearch.search.sort.SortOrder
import org.elasticsearch.indices.IndexMissingException

import javax.annotation.PreDestroy
import java.util.regex.Pattern

import static org.elasticsearch.node.NodeBuilder.nodeBuilder
import javax.annotation.PostConstruct
import org.elasticsearch.node.Node

class ElasticSearchService {

    def logService
    def grailsApplication

    private Node node
    private Client client

    @NotTransactional
    @PostConstruct
    def initialize() {
        logService.log("ElasticSearch service starting...")
        ImmutableSettings.Builder settings = ImmutableSettings.settingsBuilder();
        settings.put("path.home", grailsApplication.config.elasticsearch.location);
        node = nodeBuilder().local(true).settings(settings).node();
        client = node.client();
        client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
        logService.log("ElasticSearch service initialisation complete.")
    }

    @PreDestroy
    def destroy() {
        if (node) {
            node.close();
        }
    }

    public reinitialiseIndex() {
        try {
            def ct = new CodeTimer("Index deletion")
            node.client().admin().indices().prepareDelete("images").execute().get()
            ct.stop(true)

        } catch (Exception ex) {
            log.error(ex.getMessage(), ex)
            // failed to delete index - maybe because it didn't exist?
        }
        initialiseIndex()
    }

    def indexImage(Image image) {
        def ct = new CodeTimer("Index Image ${image.id}")
        // only add the fields that are searchable. They are marked with an annotation
        def fields = Image.class.declaredFields
        def data = [:]
        fields.each { field ->
            if (field.isAnnotationPresent(SearchableProperty)) {
                data[field.name] = image."${field.name}"
            }
        }

        def md = ImageMetaDataItem.findAllByImage(image)
        data.metadata = [:]
        md.each {
            // Keys get lowercased here and when being searched for to make the case insensitive
            data.metadata[it.name.toLowerCase()] = it.value
        }

        def json = (data as JSON).toString()

        IndexResponse response = client.prepareIndex("images", "image", image.id.toString()).setSource(json).execute().actionGet();
        ct.stop(true)
    }

    def deleteImage(Image image) {
        if (image) {
            DeleteResponse response = client.prepareDelete("images", "image", image.id.toString()).execute().actionGet();
        }
    }

    public QueryResults<Image> simpleImageSearch(String query, GrailsParameterMap params) {
        def qmap = [query: [filtered: [query:[query_string: [query: query?.toLowerCase()]]]]]
        return search(qmap, params)
    }

    public QueryResults<Image> search(Map query, GrailsParameterMap params) {
        Map qmap = null
        Map fmap = null
        if (query.query) {
            qmap = query.query
        } else {
            if (query.filter) {
                fmap = query.filter
            } else {
                qmap = query
            }
        }

        def b = client.prepareSearch("images").setSearchType(SearchType.QUERY_THEN_FETCH)
        if (qmap) {
            b.setQuery(qmap)
        }

        if (fmap) {
            b.setPostFilter(fmap)
        }

        return executeSearch(b, params)
    }

    private def initialiseIndex() {
        def mappingJson = '''
        {
            "mappings": {
                "image": {
                    "dynamic_templates": [
                    {
                        "ids" : {
                            "path_match": "metadata.*id",
                            "mapping": { "type": "string", "index" : "not_analyzed" }
                        }
                    },
                    {
                        "uids" : {
                            "path_match": "metadata.*uid",
                            "mapping": { "type": "string", "index" : "not_analyzed" }
                        }
                    }
                    ],
                    "_all": {
                        "enabled": true,
                        "store": "yes"
                    },
                    "properties": {
                        "imageIdentifier" : { "type" : "string", "index" : "not_analyzed" },
                        "originalFilename" : { "type" : "string", "index" : "not_analyzed" },
                        "mimeType" : { "type" : "string", "index" : "not_analyzed" },
                    }
                }
            }
        }
        '''

        def parsedJson = new JsonSlurper().parseText(mappingJson)
        def mappingsDoc = (parsedJson as JSON).toString()
        client.admin().indices().prepareCreate("images").setSource(mappingsDoc).execute().actionGet()

        client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet()
    }

    def searchUsingCriteria(List<SearchCriteria> criteriaList, GrailsParameterMap params) {

        def metaDataPattern = Pattern.compile("^(.*)[:](.*)\$")
        // split out by criteria type
        def criteriaMap = criteriaList.groupBy { it.criteriaDefinition.type }


        def filter  = criteriaList ? FilterBuilders.boolFilter() : FilterBuilders.matchAllFilter()

        def list = criteriaMap[CriteriaType.ImageProperty]
        if (list) {
            ESSearchCriteriaUtils.buildCriteria(filter, list)
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

                    filter.must(FilterBuilders.queryFilter(QueryBuilders.queryString("${matcher.group(1)}:${term}")))
                }
            }
        }

        return executeFilterSearch(filter, params)
    }

    public QueryResults<Image> searchByMetadata(String key, List<String> values, GrailsParameterMap params) {

        def queryString = values.collect { key.toLowerCase() + ":\"" + it + "\""}.join(" OR ")
        QueryStringQueryBuilder builder = QueryBuilders.queryStringQuery(queryString)

        //DM - Im unclear as to why this stopped working !!!
//        def filter = FilterBuilders.orFilter()
//        values.each { value ->
//            // Metadata keys are lowercased when indexed
//            filter.add(FilterBuilders.termFilter(key.toLowerCase(), value))
//        }

//        return executeFilterSearch(filter, params)
        builder.defaultField("content")
        def searchRequestBuilder = client.prepareSearch("images").setSearchType(SearchType.QUERY_THEN_FETCH)
        searchRequestBuilder.setQuery(builder)
        return executeSearch(searchRequestBuilder, params)
    }

    private QueryResults<Image> executeFilterSearch(FilterBuilder filterBuilder, GrailsParameterMap params) {
        def searchRequestBuilder = client.prepareSearch("images").setSearchType(SearchType.QUERY_THEN_FETCH)
        searchRequestBuilder.setPostFilter(filterBuilder)
        return executeSearch(searchRequestBuilder, params)
    }

    private QueryResults<Image> executeSearch(SearchRequestBuilder searchRequestBuilder, GrailsParameterMap params) {

        try {
            if (params?.offset) {
                searchRequestBuilder.setFrom(params.int("offset"))
            }

            if (params?.max) {
                searchRequestBuilder.setSize(params.int("max"))
            } else {
                searchRequestBuilder.setSize(Integer.MAX_VALUE) // probably way too many!
            }

            if (params?.sort) {
                def order = params?.order == "asc" ? SortOrder.ASC : SortOrder.DESC
                searchRequestBuilder.addSort(params.sort as String, order)
            }

            def ct = new CodeTimer("Index search")
            SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
            ct.stop(true)

            ct = new CodeTimer("Object retrieval (${searchResponse.hits.hits.length} of ${searchResponse.hits.totalHits} hits)")
            def imageList = []
            if (searchResponse.hits) {
                searchResponse.hits.each { hit ->
                    imageList << Image.get(hit.id.toLong())
                }
            }
            ct.stop(true)

            return new QueryResults<Image>(list: imageList, totalCount: searchResponse?.hits?.totalHits ?: 0)
        } catch (SearchPhaseExecutionException e) {
            log.warn(".SearchPhaseExecutionException thrown - this is expected behaviour for a new empty system.")
            return new QueryResults<Image>(list: [], totalCount: 0)
        } catch (IndexMissingException e) {
            log.warn("IndexMissingException thrown - this is expected behaviour for a new empty system.")
            return new QueryResults<Image>(list: [], totalCount: 0)
        }
    }

    def getMetadataKeys() {
        ClusterState cs = client.admin().cluster().prepareState().execute().actionGet().getState();
        IndexMetaData imd = cs.getMetaData().index("images")
        Map mdd = imd.mapping("image").sourceAsMap()
        Map metadata = mdd?.properties?.metadata?.properties
        def names = []
        if (metadata) {
            names = metadata.collect { it.key }
        }
        return names
    }

    def ping() {
        logService.log("ElasticSearch Service is ${node ? '' : 'NOT' } alive.")
    }
}
