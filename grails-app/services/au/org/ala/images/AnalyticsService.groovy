package au.org.ala.images

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.services.analytics.AnalyticsScopes
import grails.gorm.transactions.Transactional
import groovy.json.JsonSlurper

@Transactional
class AnalyticsService {

    def collectoryService
    def grailsApplication



    def REPORT_PERIODS = [
        "thisMonth": "30daysAgo",
        "last3Months": "90daysAgo",
        "lastYear": "365daysAgo"
    ]

    Object byDataResource(String dataResourceUID) {

        if (!dataResourceUID){
            return null
        } else {

            def now = (new Date() + 1 ).format( 'yyyy-MM-dd' )
            def results = [:]
            //do authentication....
            def googleApiBaseUrl = grailsApplication.config.analytics.baseURL
            def googleViewID = URLEncoder.encode(grailsApplication.config.analytics.viewID, "UTF-8")

            REPORT_PERIODS.each { label, period ->
                def lastMonth = "${googleApiBaseUrl}?ids=${googleViewID}&start-date=30daysAgo&end-date=${now}&dimensions=ga%3AeventCategory&metrics=ga%3AuniqueEvents&filters=ga%3AeventAction%3D%3D${dataResourceUID}&access_token=${accessToken}"
                def js = new JsonSlurper()
                def rows = js.parse(new URL(lastMonth)).rows
                def totalEvents = 0
                rows.each {k, v -> totalEvents+= v as Integer}
                results[label] = ["totalEvents": totalEvents, "events": js.parse(new URL(lastMonth)).rows]
            }
            results
        }
    }

    Object byAll() {

        def now = (new Date() + 1 ).format( 'yyyy-MM-dd' )
        def results = [:]
        def googleApiBaseUrl = grailsApplication.config.analytics.baseURL
        def googleViewID = URLEncoder.encode(grailsApplication.config.analytics.viewID, "UTF-8")

        REPORT_PERIODS.each { label, period ->
            def lastMonth = "${googleApiBaseUrl}?ids=${googleViewID}&start-date=${period}&end-date=${now}&dimensions=ga%3AeventAction&metrics=ga%3AuniqueEvents&&access_token=${getAccessToken()}"
            def js = new JsonSlurper()
            def totalEvents = 0
            def rows = js.parse(new URL(lastMonth)).rows
            def enhanced = []
            rows.each { key, value ->
                enhanced << ["uid" : key, "name": collectoryService.getNameForUID(key), "count": value]
            }

            enhanced.sort { }

            rows.each {k, v -> totalEvents+= v as Integer}
            results[label] = [
                    "totalEvents": totalEvents,
                    "entities": enhanced
            ]
        }
        results
    }

    String getAccessToken(){
        GoogleCredential credential = GoogleCredential
                .fromStream(new FileInputStream("/data/image-service/config/google-analytics.json"))
                .createScoped(Collections.singleton(AnalyticsScopes.ANALYTICS_READONLY));
        credential.refreshToken()
        credential.getAccessToken()
    }
}
