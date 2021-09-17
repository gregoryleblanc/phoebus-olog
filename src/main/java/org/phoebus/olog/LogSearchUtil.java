package org.phoebus.olog;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.disMaxQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.wildcardQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchPhraseQuery;
import static org.elasticsearch.index.query.QueryBuilders.fuzzyQuery;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.DisMaxQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

/**
 * A utility class for creating a search query for log entries based on time,
 * logbooks, tags, properties, description, etc.
 *
 * @author Kunal Shroff
 *
 */
@Service
public class LogSearchUtil
{

    final private static String MILLI_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
    final public static DateTimeFormatter MILLI_FORMAT = DateTimeFormatter.ofPattern(MILLI_PATTERN).withZone(ZoneId.systemDefault());

    @Value("${elasticsearch.log.index:olog_logs}")
    private String ES_LOG_INDEX;
    @Value("${elasticsearch.log.type:olog_log}")
    private String ES_LOG_TYPE;
    @Value("${elasticsearch.result.size.search.default:100}")
    private int defaultSearchSize;
    @Value("${elasticsearch.result.size.search.max:1000}")
    private int maxSearchSize;

    /**
     *
     * @param searchParameters - the various search parameters
     * @return A {@link SearchRequest} based on the provided search parameters
     */
    public synchronized SearchRequest buildSearchRequest(MultiValueMap<String, String> searchParameters)
    {
        SearchRequest searchRequest = new SearchRequest(ES_LOG_INDEX+"*");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        // The default temporal range for the query
        boolean fuzzySearch = false;
        List<String> searchTerms = new ArrayList<>();
        List<String> titleSearchTerms = new ArrayList<>();
        List<String> levelSearchTerms = new ArrayList<>();
        boolean temporalSearch = false;
        Instant start = Instant.EPOCH;
        Instant end = Instant.now();
        boolean includeEvents = false;

        int searchResultSize = defaultSearchSize;

        for (Entry<String, List<String>> parameter : searchParameters.entrySet())
        {
            switch (parameter.getKey().strip().toLowerCase()) {
            case "desc":
            case "description":
                for (String value : parameter.getValue())
                {
                    for (String pattern : value.split("[\\|,;\\s+]"))
                    {
                        searchTerms.add(pattern.trim().toLowerCase());
                    }
                }
                break;
            case "title":
                for (String value : parameter.getValue())
                {
                    for (String pattern : value.split("[\\|,;\\s+]"))
                    {
                        titleSearchTerms.add(pattern.trim().toLowerCase());
                    }
                }
                break;
            case "fuzzy":
                fuzzySearch = true;
                break;
            case "phrase":
                DisMaxQueryBuilder exactQuery = disMaxQuery();
                for (String value : parameter.getValue())
                {
                    exactQuery.add(matchPhraseQuery("description", value.trim().toLowerCase()));
                }
                boolQuery.must(exactQuery);
                break;
            case "owner":
                DisMaxQueryBuilder ownerQuery = disMaxQuery();
                for (String value : parameter.getValue())
                {
                    for (String pattern : value.split("[\\|,;\\s+]"))
                    {
                        ownerQuery.add(wildcardQuery("owner", pattern.trim()));
                    }
                }
                boolQuery.must(ownerQuery);
                break;
            case "tags":
                DisMaxQueryBuilder tagQuery = disMaxQuery();
                for (String value : parameter.getValue())
                {
                    for (String pattern : value.split("[\\|,;]"))
                    {
                        tagQuery.add(wildcardQuery("tags.name", pattern.trim()));
                    }
                }
                boolQuery.must(nestedQuery("tags", tagQuery, ScoreMode.None));
                break;
            case "logbooks":
                DisMaxQueryBuilder logbookQuery = disMaxQuery();
                for (String value : parameter.getValue())
                {
                    for (String pattern : value.split("[\\|,;]"))
                    {
                        logbookQuery.add(wildcardQuery("logbooks.name", pattern.trim()));
                    }
                }
                boolQuery.must(nestedQuery("logbooks", logbookQuery, ScoreMode.None));
                break;
            case "start":
                // If there are multiple start times submitted select the earliest
                Instant earliestStartTime = Instant.now();
                for (String value : parameter.getValue())
                {
                    Instant time = Instant.from(MILLI_FORMAT.parse(value));
                    earliestStartTime = earliestStartTime.isBefore(time) ? earliestStartTime : time;
                }
                temporalSearch = true;
                start = earliestStartTime;
                break;
            case "end":
                // If there are multiple end times submitted select the latest
                Instant latestEndTime = Instant.MIN;
                for (String value : parameter.getValue())
                {
                    Instant time = Instant.from(MILLI_FORMAT.parse(value));
                    latestEndTime = latestEndTime.isBefore(time) ? time : latestEndTime;
                }
                temporalSearch = true;
                end = latestEndTime;
                break;
            case "includeevents":
            case "includeevent":
                includeEvents = true;
                break;
            case "properties":
                DisMaxQueryBuilder propertyQuery = disMaxQuery();
                for (String value : parameter.getValue()) {
                    for (String pattern : value.split("[\\|,;]")) {
                        String[] propertySearchFields;
                        propertySearchFields = Arrays.copyOf(pattern.split("\\."), 3);

                        BoolQueryBuilder bq = boolQuery();
                        if (propertySearchFields[0] != null && !propertySearchFields[0].isEmpty())
                        {
                            bq.must(wildcardQuery("properties.name", propertySearchFields[0].trim()));
                        }

                        if (propertySearchFields[1] != null && !propertySearchFields[1].isEmpty())
                        {
                            BoolQueryBuilder bq2 = boolQuery();
                            bq2.must(wildcardQuery("properties.attributes.name", propertySearchFields[1].trim()));
                            if (propertySearchFields[2] != null && !propertySearchFields[2].isEmpty()){
                                bq2.must(wildcardQuery("properties.attributes.value", propertySearchFields[2].trim()));
                            }
                            bq.must(nestedQuery("properties.attributes", bq2, ScoreMode.None));
                        }
                        
                        propertyQuery.add(nestedQuery("properties", bq, ScoreMode.None));
                    }
                }
                boolQuery.must(propertyQuery);
                break;
            case "level":
                for (String value : parameter.getValue())
                {
                    for (String pattern : value.split("[\\|,;\\s+]"))
                    {
                        levelSearchTerms.add(pattern.trim().toLowerCase());
                    }
                }
                break;
            case "limit":
                try {
                    searchResultSize = Integer.parseInt(parameter.getValue().get(0));
                } catch (Exception e) {
                    Logger.getLogger(LogSearchUtil.class.getName())
                            .log(Level.WARNING, "Encountered unparsable 'limit' value", e);
                }
                break;
            default:
                // Unsupported search parameters are ignored
                break;
            }
        }
        // Add the temporal queries
        if(temporalSearch)
        {
            // check that the start is before the end
            if (start.isBefore(end))
            {
                if (includeEvents)
                {
                    DisMaxQueryBuilder temporalQuery = disMaxQuery();
                    // Add a query based on the create time
                    temporalQuery.add(rangeQuery("createdDate").from(start.toEpochMilli()).to(end.toEpochMilli()));
                    // Add a query based on the time of the associated events
                    temporalQuery.add(nestedQuery("events",
                                                  rangeQuery("events.instant").from(start.toEpochMilli()).to(end.toEpochMilli()),
                                                  ScoreMode.None));
                    boolQuery.must(temporalQuery);
                }
                else {
                    boolQuery.must(rangeQuery("createdDate").from(start.toEpochMilli()).to(end.toEpochMilli()));
                }
            }
        }
        // Add the description query
        if (!searchTerms.isEmpty())
        {
            DisMaxQueryBuilder descQuery = disMaxQuery();
            if (fuzzySearch)
            {
                searchTerms.stream().forEach(searchTerm -> {
                    descQuery.add(fuzzyQuery("description", searchTerm));
                });
            } else
            {
                searchTerms.stream().forEach(searchTerm -> {
                    descQuery.add(wildcardQuery("description", searchTerm));
                });
            }
            boolQuery.must(descQuery);
        }
        // Add the title query
        if (!titleSearchTerms.isEmpty())
        {
            DisMaxQueryBuilder titleQuery = disMaxQuery();
            if (fuzzySearch)
            {
                titleSearchTerms.stream().forEach(searchTerm -> {
                    titleQuery.add(fuzzyQuery("title", searchTerm));
                });
            } else
            {
                titleSearchTerms.stream().forEach(searchTerm -> {
                    titleQuery.add(wildcardQuery("title", searchTerm));
                });
            }
            boolQuery.must(titleQuery);
        }
        // Add the level query
        if (!levelSearchTerms.isEmpty())
        {
            DisMaxQueryBuilder levelQuery = disMaxQuery();
            if (fuzzySearch)
            {
                levelSearchTerms.stream().forEach(searchTerm -> {
                    levelQuery.add(fuzzyQuery("level", searchTerm));
                });
            } else
            {
                levelSearchTerms.stream().forEach(searchTerm -> {
                    levelQuery.add(wildcardQuery("level", searchTerm));
                });
            }
            boolQuery.must(levelQuery);
        }

        searchSourceBuilder.sort("createdDate", SortOrder.DESC);
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.from(0);
        searchSourceBuilder.size(Math.min(searchResultSize, maxSearchSize));

        searchSourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));

        searchRequest.source(searchSourceBuilder);
        return searchRequest;
    }
}
