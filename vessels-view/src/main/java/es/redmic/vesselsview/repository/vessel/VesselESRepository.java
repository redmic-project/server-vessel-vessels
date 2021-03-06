package es.redmic.vesselsview.repository.vessel;

/*-
 * #%L
 * Vessels-query-endpoint
 * %%
 * Copyright (C) 2019 REDMIC Project / Server
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.MultiSearchResponse.Item;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Repository;

import es.redmic.elasticsearchlib.data.repository.RWDataESRepository;
import es.redmic.exception.common.ExceptionType;
import es.redmic.exception.elasticsearch.ESQueryException;
import es.redmic.models.es.common.dto.EventApplicationResult;
import es.redmic.models.es.common.query.dto.MetadataQueryDTO;
import es.redmic.vesselsview.model.vessel.Vessel;
import es.redmic.vesselsview.model.vesseltype.VesselType;
import es.redmic.viewlib.data.repository.IDataRepository;

@Repository
public class VesselESRepository extends RWDataESRepository<Vessel, MetadataQueryDTO>
		implements IDataRepository<Vessel, MetadataQueryDTO> {

	private static String[] INDEX = { "platform" };
	private static String TYPE = "vessel";

	// @formatter:off
 
		private final String ID_PROPERTY = "id",
				MMSI_PROPERTY = "mmsi",
				IMO_PROPERTY = "imo";
	// @formatter:on

	public VesselESRepository() {
		super(INDEX, TYPE);
	}

	@SuppressWarnings("unchecked")
	public EventApplicationResult updateVesselTypeInVessel(String vesselId, VesselType vesselType) {

		XContentBuilder doc;

		try {
			doc = jsonBuilder().startObject().field("type", objectMapper.convertValue(vesselType, Map.class))
					.endObject();
		} catch (IllegalArgumentException | IOException e1) {
			LOGGER.debug("Error modificando el item con id " + vesselId + " en " + getIndex()[0] + " " + getType());
			return new EventApplicationResult(ExceptionType.ES_UPDATE_DOCUMENT.toString());
		}

		return update(vesselId, doc);
	}

	@Override
	protected EventApplicationResult checkInsertConstraintsFulfilled(Vessel modelToIndex) {

		// @formatter:off

		QueryBuilder idTerm = QueryBuilders.termQuery(ID_PROPERTY, modelToIndex.getId()),
				mmsiTerm = QueryBuilders.termQuery(MMSI_PROPERTY, modelToIndex.getMmsi()),
				imoTerm = null;
		
		if (modelToIndex.getImo() != null) {
			imoTerm = QueryBuilders.termQuery(IMO_PROPERTY, modelToIndex.getImo());
		}
		
		MultiSearchRequest request = new MultiSearchRequest();
		
		SearchSourceBuilder requestBuilderId = new SearchSourceBuilder().query(idTerm).size(1),
			requestBuilderMmsi = new SearchSourceBuilder().query(mmsiTerm).size(1),
			requestBuilderImo = new SearchSourceBuilder().query(imoTerm).size(1);

		request
			.add(new SearchRequest().indices(getIndex()).source(requestBuilderId))
			.add(new SearchRequest().indices(getIndex()).source(requestBuilderMmsi));

		if (imoTerm != null)
			request.add(new SearchRequest().indices(getIndex()).source(requestBuilderImo));
		
		MultiSearchResponse sr;
		try {
			sr = ESProvider.getClient().msearch(request, RequestOptions.DEFAULT);
		} catch (IOException e) {
			e.printStackTrace();
			throw new ESQueryException();
		}

		// @formatter:on

		Map<String, String> arguments = new HashMap<>();

		Item[] responses = sr.getResponses();

		if (responses != null && responses[0].getResponse().getHits().getTotalHits() > 0) {
			arguments.put(ID_PROPERTY, modelToIndex.getId());
		}

		if (responses != null && responses[1].getResponse().getHits().getTotalHits() > 0) {
			arguments.put(MMSI_PROPERTY, modelToIndex.getMmsi().toString());
		}

		if (imoTerm != null && responses != null && responses[2].getResponse().getHits().getTotalHits() > 0) {
			arguments.put(IMO_PROPERTY, modelToIndex.getImo().toString());
		}

		if (arguments.size() > 0) {
			return new EventApplicationResult(ExceptionType.ES_INSERT_DOCUMENT.toString(), arguments);
		}

		return new EventApplicationResult(true);
	}

	@Override
	protected EventApplicationResult checkUpdateConstraintsFulfilled(Vessel modelToIndex) {

		// @formatter:off

		BoolQueryBuilder mmsiTerm = QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery(MMSI_PROPERTY, modelToIndex.getMmsi()))
				.mustNot(QueryBuilders.termQuery(ID_PROPERTY, modelToIndex.getId())),
			imoTerm = null;
		
		if (modelToIndex.getImo() != null) {
			imoTerm = QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery(IMO_PROPERTY, modelToIndex.getImo()))
				.mustNot(QueryBuilders.termQuery(ID_PROPERTY, modelToIndex.getId()));
		}
		
		MultiSearchRequest request = new MultiSearchRequest();
		
		SearchSourceBuilder requestBuilderMmsi = new SearchSourceBuilder().query(mmsiTerm).size(1),
				requestBuilderImo = null;
		
		if (imoTerm != null) {
			requestBuilderImo = new SearchSourceBuilder().query(imoTerm).size(1);
		}

		request.add(new SearchRequest().indices(getIndex()).source(requestBuilderMmsi));
		
		if (requestBuilderImo != null) {
			request.add(new SearchRequest().indices(getIndex()).source(requestBuilderImo));
		}
		
		MultiSearchResponse sr;
		try {
			sr = ESProvider.getClient().msearch(request, RequestOptions.DEFAULT);
		} catch (IOException e) {
			e.printStackTrace();
			throw new ESQueryException();
		}

		// @formatter:on

		Map<String, String> arguments = new HashMap<>();

		Item[] responses = sr.getResponses();

		if (responses != null && responses[0].getResponse().getHits().getTotalHits() > 0) {
			arguments.put(MMSI_PROPERTY, modelToIndex.getMmsi().toString());
		}

		if (requestBuilderImo != null && responses != null && responses[1].getResponse().getHits().getTotalHits() > 0) {
			arguments.put(IMO_PROPERTY, modelToIndex.getImo().toString());
		}

		if (arguments.size() > 0) {
			return new EventApplicationResult(ExceptionType.ES_UPDATE_DOCUMENT.toString(), arguments);
		}

		return new EventApplicationResult(true);
	}

	@Override
	protected EventApplicationResult checkDeleteConstraintsFulfilled(String modelToIndexId) {
		return new EventApplicationResult(true);
	}
}
