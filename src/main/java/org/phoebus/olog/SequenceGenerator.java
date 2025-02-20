/**
 * 
 */
package org.phoebus.olog;

import java.io.IOException;

import javax.annotation.PostConstruct;

import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author Kunal Shroff
 *
 */
@Service
public class SequenceGenerator
{

    @Value("${elasticsearch.sequence.index:olog_sequence}")
    private String ES_SEQUENCE_INDEX;
    @Value("${elasticsearch.sequence.type:olog_sequence}")
    private String ES_SEQUENCE_TYPE;

    @Autowired
    @Qualifier("indexClient")
    RestHighLevelClient indexClient;

    private static RestHighLevelClient client;


    @PostConstruct
    public void init()
    {
        Application.logger.config("Initializing the unique sequence id generator");
        SequenceGenerator.client = indexClient;
    }

    /**
     * get a new unique id from the olog_sequnce index
     * 
     * @return a new unique id for a olog entry
     * @throws IOException The Elasticsearch client may throw this
     */
    public long getID() throws IOException
    {
        IndexResponse response = client.index(
                new IndexRequest(ES_SEQUENCE_INDEX, ES_SEQUENCE_TYPE, "id").source(0, XContentType.class),
                RequestOptions.DEFAULT);
        return response.getVersion();
    }

}
