package org.lambda3.graphene.core;

/*-
 * ==========================License-Start=============================
 * Graphene.java - Graphene Core - Lambda^3 - 2017
 * Graphene
 * %%
 * Copyright (C) 2017 Lambda^3
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * ==========================License-End===============================
 */


import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.lambda3.graphene.core.coreference.CoreferenceResolver;
import org.lambda3.graphene.core.coreference.model.CoreferenceContent;
import org.lambda3.graphene.core.relation_extraction.RelationExtractor;
import org.lambda3.graphene.core.utils.ConfigUtils;
import org.lambda3.text.simplification.discourse.model.SimplificationContent;
import org.lambda3.text.simplification.discourse.processing.DiscourseSimplifier;
import org.lambda3.text.simplification.discourse.processing.ProcessingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

public class Graphene {
	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Config config;

	private final CoreferenceResolver coreference;
	private final DiscourseSimplifier discourseSimplificationRunner;
	private final RelationExtractor relationExtractor;

	public Graphene() {
		this(ConfigFactory.load());
	}

	public Graphene(Config config) {
		this.config = config
			.withFallback(ConfigFactory.load("build"))
			.getConfig("graphene");

		this.coreference = getCoreferenceResolver(this.config);
		this.discourseSimplificationRunner = new DiscourseSimplifier(this.config.getConfig("discourse-simplification"));
		this.relationExtractor = new RelationExtractor(this.config.getConfig("relation-extraction"));

		log.info("Graphene initialized");
		log.info("\n{}", ConfigUtils.prettyPrint(this.config));
	}

	private CoreferenceResolver getCoreferenceResolver(Config config) {
		String className = config.getString("coreference.resolver");
		CoreferenceResolver coreferenceResolver = null;

		log.info("Load Coreference-Resolver: '" + className + "'");
		try {
			Class<?> clazz = Class.forName(className);
			Constructor[] constructors = clazz.getConstructors();

			if (CoreferenceResolver.class.isAssignableFrom(clazz)) {
				// It's our internal factory hence we inject the core dependency.
				coreferenceResolver = (CoreferenceResolver) constructors[0].newInstance(config.getConfig("coreference.settings"));
			}
		} catch (Exception e) {
			throw new RuntimeException("Fail to initialize CoreferenceResolver: " + className, e);
		}
		if (coreferenceResolver == null) {
			throw new RuntimeException("Fail to initialize CoreferenceResolver: " + className);
		}

		return coreferenceResolver;
	}

	public CoreferenceContent doCoreference(String text) {
		log.debug("[co-reference] running...");
		final CoreferenceContent content = coreference.doCoreferenceResolution(text);
		log.debug("[co-reference] done!");
		return content;
	}

	public SimplificationContent doDiscourseSimplification(String text, boolean doCoreference, boolean isolateSentences) {
		if (doCoreference) {
			final CoreferenceContent cc = doCoreference(text);
			text = cc.getSubstitutedText();
		}

		log.debug("[discourse simplification] running...");
		final SimplificationContent sc = discourseSimplificationRunner.doDiscourseSimplification(text, (isolateSentences)? ProcessingType.SEPARATE : ProcessingType.WHOLE);
		sc.setCoreferenced(doCoreference);
		log.debug("[discourse simplification] done!");
		return sc;
	}

	public SimplificationContent doRelationExtraction(String text, boolean doCoreference, boolean isolateSentences, boolean doComplexCategoryExtraction) {
		final SimplificationContent content = doDiscourseSimplification(text, doCoreference, isolateSentences);
        extractRelations(content);
        return content;
	}

	public void extractRelations(SimplificationContent content) {
		log.debug("[relation extraction] running...");
		relationExtractor.extract(content);
		log.debug("[relation extraction] done!");
	}

	public VersionInfo getVersionInfo() {
		if (log.isDebugEnabled()) {
			log.debug("getVersionInfo");
		}
		return new VersionInfo(
                config.getString("version.name"),
                config.getString("version.version"),
                config.getString("version.build-number")
        );
	}
}
