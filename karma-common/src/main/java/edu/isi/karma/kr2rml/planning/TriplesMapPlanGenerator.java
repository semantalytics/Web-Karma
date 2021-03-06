/*******************************************************************************
 * Copyright 2014 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/
package edu.isi.karma.kr2rml.planning;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.isi.karma.kr2rml.KR2RMLRDFWriter;
import edu.isi.karma.kr2rml.template.PopulatedTemplateTermSet;
import edu.isi.karma.rep.Row;

public class TriplesMapPlanGenerator {
	private static Logger LOG = LoggerFactory.getLogger(TriplesMapPlanGenerator.class);
	
	private Map<TriplesMap, TriplesMapWorkerPlan> triplesMapToWorkerPlan;
	private Set<String> unprocessedTriplesMapsIds = new HashSet<String>();
	private Row r;
	private KR2RMLRDFWriter outWriter;
	
	public TriplesMapPlanGenerator(Map<TriplesMap, TriplesMapWorkerPlan> triplesMapToWorkerPlan, Row r, KR2RMLRDFWriter outWriter) {
		
		this.triplesMapToWorkerPlan = triplesMapToWorkerPlan;
		this.r = r;
		this.outWriter = outWriter;
	}

	public TriplesMapPlan generatePlan(TriplesMapGraphMerger tmf)
	{
		List<TriplesMapWorker> workers = new LinkedList<TriplesMapWorker>();
		Map<String, List<PopulatedTemplateTermSet>>triplesMapSubjects = new ConcurrentHashMap<String, List<PopulatedTemplateTermSet>>();
		TriplesMapPlan plan = new TriplesMapPlan(workers, r, triplesMapSubjects);
		
		List<TriplesMapGraph> graphs = tmf.getGraphs();
		for(TriplesMapGraph graph : graphs)
		{
			//This can end up in deadlock.
			workers.addAll(generatePlan(graph, plan).values());
		}
		return plan;
	}

	public TriplesMapPlan generatePlan(TriplesMapGraph graph, List<String> triplesMapProcessingOrder)
	{
		List<TriplesMapWorker> workers = new LinkedList<TriplesMapWorker>();
		Map<String, List<PopulatedTemplateTermSet>>triplesMapSubjects = new ConcurrentHashMap<String, List<PopulatedTemplateTermSet>>();
		TriplesMapPlan plan = new TriplesMapPlan(workers, r, triplesMapSubjects);
		Map<TriplesMap, TriplesMapWorker> mapToWorker = generatePlan(graph, plan);
		for(String triplesMapId : triplesMapProcessingOrder)
		{
			TriplesMap map = graph.getTriplesMap(triplesMapId);
			TriplesMapWorker worker = mapToWorker.get(map);
			if(worker != null)
			{
				workers.add(worker);
			}
			else
			{
				LOG.error("Graph is disconnected from " + triplesMapId );
			}
			
			
		}
		
		return plan;
	}
	private Map<TriplesMap, TriplesMapWorker> generatePlan(TriplesMapGraph graph, TriplesMapPlan plan)
	{
		unprocessedTriplesMapsIds.addAll(graph.getTriplesMapIds());
		//add strategy
		Map<TriplesMap, TriplesMapWorker> mapToWorker = new HashMap<TriplesMap, TriplesMapWorker>();
		String triplesMapId = graph.findRoot(new SteinerTreeRootStrategy(new WorksheetDepthRootStrategy()));
		
		do
		{
			if(triplesMapId == null)
			{
				triplesMapId = unprocessedTriplesMapsIds.iterator().next();
			}
			TriplesMap map = graph.getTriplesMap(triplesMapId);
			generateTriplesMapWorker(mapToWorker, graph, map, plan);	
			triplesMapId = null;
		}
		while(!unprocessedTriplesMapsIds.isEmpty());

		
		return mapToWorker;
	}
	private void generateTriplesMapWorker(
			Map<TriplesMap, TriplesMapWorker> mapToWorker,
			TriplesMapGraph graph, TriplesMap map, TriplesMapPlan plan) {
		
		if(!unprocessedTriplesMapsIds.remove(map.getId()))
		{
			LOG.error("already visited " + map.toString());
			return;
		}
		List<TriplesMapLink> links = graph.getAllNeighboringTriplesMap(map.getId());
		
		
		List<TriplesMapWorker> workersDependentOn = new LinkedList<TriplesMapWorker>();
		for(TriplesMapLink link : links)
		{
			if((link.getSourceMap() == map && !link.isFlipped()) || (link.getTargetMap() == map && link.isFlipped()))
			{
				TriplesMap mapDependedOn = link.getSourceMap()==map? link.getTargetMap() : link.getSourceMap(); 
				if(!mapToWorker.containsKey(mapDependedOn))
				{
					generateTriplesMapWorker(mapToWorker, graph, mapDependedOn, plan);
				}
				workersDependentOn.add(mapToWorker.get(mapDependedOn));
			}
		}
		TriplesMapWorker newWorker = new TriplesMapWorker(map, new CountDownLatch(workersDependentOn.size()), r, triplesMapToWorkerPlan.get(map), outWriter);
		mapToWorker.put(map, newWorker);
		
		for(TriplesMapWorker worker : workersDependentOn)
		{
			worker.addDependentTriplesMapLatch(newWorker.getLatch());
		}
	}
}
