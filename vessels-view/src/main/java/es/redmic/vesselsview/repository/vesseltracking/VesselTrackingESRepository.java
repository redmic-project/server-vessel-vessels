package es.redmic.vesselsview.repository.vesseltracking;

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
import org.joda.time.format.DateTimeFormat;
import org.springframework.stereotype.Repository;

import es.redmic.elasticsearchlib.geodata.repository.RWGeoDataESRepository;
import es.redmic.exception.common.ExceptionType;
import es.redmic.exception.elasticsearch.ESQueryException;
import es.redmic.models.es.common.dto.EventApplicationResult;
import es.redmic.models.es.common.query.dto.DataQueryDTO;
import es.redmic.vesselslib.utils.VesselTrackingUtil;
import es.redmic.vesselsview.model.vessel.Vessel;
import es.redmic.vesselsview.model.vesseltracking.VesselTracking;
import es.redmic.viewlib.geodata.repository.IGeoDataRepository;

@Repository
public class VesselTrackingESRepository extends RWGeoDataESRepository<VesselTracking, DataQueryDTO>
		implements IGeoDataRepository<VesselTracking, DataQueryDTO> {

	private static String[] INDEX = { "tracking-vessel" };
	private static String TYPE = "_doc";

	// @formatter:off
 
	private final String ID_PROPERTY = "id",
			UUID_PROPERTY = "uuid",
			MMSI_PROPERTY = "properties.vessel.mmsi",
			DATE_PROPERTY = "properties.date",
			VESSEL_PROPERTY = "properties.vessel";
	
	protected static final Boolean ROLLOVER_INDEX = true;
	// @formatter:on

	public VesselTrackingESRepository() {
		super(INDEX, TYPE, ROLLOVER_INDEX);
	}

	@SuppressWarnings("unchecked")
	public EventApplicationResult updateVesselTypeInVessel(String vesselTrackingId, Vessel vessel) {

		XContentBuilder doc;

		try {
			doc = jsonBuilder().startObject().field(VESSEL_PROPERTY, objectMapper.convertValue(vessel, Map.class))
					.endObject();
		} catch (IllegalArgumentException | IOException e1) {
			LOGGER.debug(
					"Error modificando el item con id " + vesselTrackingId + " en " + getIndex()[0] + " " + getType());
			return new EventApplicationResult(ExceptionType.ES_UPDATE_DOCUMENT.toString());
		}

		return update(vesselTrackingId, doc);
	}

	/**
	 * Función que comprueba que un elemento puede ser añadido a elasticsearch
	 * cumpliendo todas las restricciones
	 * 
	 * Que no exista un elemento con el mismo id. | Que no exista un elemento con el
	 * mismo mmsi para la misma fecha. | En caso de ser un elemento procesado (No
	 * speed layer), no debe existir un elmento con el mismo uuid
	 */

	@Override
	protected EventApplicationResult checkInsertConstraintsFulfilled(VesselTracking modelToIndex) {

		boolean notProcessed = modelToIndex.getUuid().equals(VesselTrackingUtil.UUID_DEFAULT);

		// @formatter:off

		QueryBuilder idTerm = QueryBuilders.termQuery(ID_PROPERTY, modelToIndex.getId()),
				mmsiTerm = QueryBuilders.boolQuery()
						.must(QueryBuilders.termQuery(MMSI_PROPERTY, modelToIndex.getProperties().getVessel().getMmsi()))
						.must(QueryBuilders.termQuery(DATE_PROPERTY, modelToIndex.getProperties().getDate())),
				auxTerm = QueryBuilders.boolQuery()
					.must(QueryBuilders.termQuery(ID_PROPERTY, modelToIndex.getId()))
					.must(QueryBuilders.termQuery(UUID_PROPERTY, modelToIndex.getUuid())),
				uuidTerm = null;
		
		if (!notProcessed) { // Si para este id existe un item ya metido, debe ser un item no procesado.
			uuidTerm = QueryBuilders.boolQuery()
					.should(QueryBuilders.boolQuery()
							.must(QueryBuilders.termQuery(ID_PROPERTY, modelToIndex.getId()))
							.mustNot(QueryBuilders.termQuery(UUID_PROPERTY, VesselTrackingUtil.UUID_DEFAULT)))
					.should(auxTerm);
		}
		else {
			uuidTerm = auxTerm;
		}
		
		MultiSearchRequest request = new MultiSearchRequest();
		
		SearchSourceBuilder requestBuilderId = new SearchSourceBuilder().query(idTerm).size(1),
			requestBuilderMmsi = new SearchSourceBuilder().query(mmsiTerm).size(1),
			requestBuilderUuid = new SearchSourceBuilder().query(uuidTerm).size(1);

		request
			.add(new SearchRequest().indices(getIndex()).source(requestBuilderId))
			.add(new SearchRequest().indices(getIndex()).source(requestBuilderMmsi))
			.add(new SearchRequest().indices(getIndex()).source(requestBuilderUuid));
		
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

		if (responses == null) {
			return new EventApplicationResult(true);
		}

		// Si el índice no existe, se da por hecho que no existe el item con esa fecha
		// en elastic
		if (indexNoExistResponse(responses)) {
			return new EventApplicationResult(true);
		}

		if (responses[0].getResponse().getHits().getTotalHits() > 0) {
			arguments.put(ID_PROPERTY, modelToIndex.getId());
		}

		if (responses[1].getResponse().getHits().getTotalHits() > 0) {
			arguments.put(MMSI_PROPERTY, modelToIndex.getProperties().getVessel().getMmsi().toString());
			arguments.put(DATE_PROPERTY, modelToIndex.getProperties().getDate().toString());
		}

		if (responses[2].getResponse().getHits().getTotalHits() > 0) {
			arguments.put(UUID_PROPERTY, modelToIndex.getUuid().toString());
		} else if (!notProcessed) { // Si no es un item no procesado y el uuid del item almacenado no es diferente a
									// not_process entonces no se tienen en cuenta los conflictos
			return new EventApplicationResult(true);
		}

		if (arguments.size() > 0) {
			return new EventApplicationResult(ExceptionType.ES_INSERT_DOCUMENT.toString(), arguments);
		}

		return new EventApplicationResult(true);
	}

	/**
	 * Función que comprueba que un elemento puede ser editado cumpliendo todas las
	 * restricciones
	 * 
	 * Que no exista un elemento con diferente id, pero el mismo mmsi y la misma
	 * fecha (Que ya exista). | Que no exista ese uuid en otro elemento
	 */

	// TODO: Controlar ediciones de items no procesados
	@Override
	protected EventApplicationResult checkUpdateConstraintsFulfilled(VesselTracking modelToIndex) {

		// @formatter:off

		BoolQueryBuilder mmsiTerm = QueryBuilders.boolQuery()
					.must(QueryBuilders.termQuery(MMSI_PROPERTY, modelToIndex.getProperties().getVessel().getMmsi()))
					.must(QueryBuilders.termQuery(DATE_PROPERTY, modelToIndex.getProperties().getDate()))
					.mustNot(QueryBuilders.termQuery(ID_PROPERTY, modelToIndex.getId())),
				idTerm = QueryBuilders.boolQuery()
					.must(QueryBuilders.termQuery(ID_PROPERTY, modelToIndex.getId()))
					.mustNot(QueryBuilders.termQuery(UUID_PROPERTY, modelToIndex.getUuid()));
		
		MultiSearchRequest request = new MultiSearchRequest();
		
		SearchSourceBuilder requestBuilderMmsi = new SearchSourceBuilder().query(mmsiTerm).size(1),
				requestBuilderId = null;
		
		if (idTerm != null) {
			requestBuilderId = new SearchSourceBuilder().query(idTerm).size(1);
		}

		request.add(new SearchRequest().indices(getIndex()).source(requestBuilderMmsi));
		
		if (requestBuilderId != null) {
			request.add(new SearchRequest().indices(getIndex()).source(requestBuilderId));
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
			arguments.put(MMSI_PROPERTY, modelToIndex.getProperties().getVessel().getMmsi().toString());
			arguments.put(DATE_PROPERTY, modelToIndex.getProperties().getDate().toString());
		}

		if (responses != null && requestBuilderId != null && responses[1].getResponse().getHits().getTotalHits() > 0) {
			arguments.put(ID_PROPERTY, modelToIndex.getId().toString());
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

	@Override
	protected String getIndex(final VesselTracking modelToIndex) {
		return getIndex()[0] + "-"
				+ modelToIndex.getProperties().getDate().toString(DateTimeFormat.forPattern("yyyy-MM-dd"));
	};
}
