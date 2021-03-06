/**
 * Copyright (C) 2010-2018 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.graphql;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.ResponseLoggingFilter;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Group;
import org.structr.core.entity.MailTemplate;
import org.structr.core.entity.Principal;
import org.structr.core.entity.Relation;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyKey;
import org.structr.rest.common.StructrGraphQLTest;
import org.structr.schema.export.StructrSchema;
import org.structr.schema.json.JsonObjectType;
import org.structr.schema.json.JsonSchema;

/**
 *
 *
 */
public class GraphQLTest extends StructrGraphQLTest {

	private static final Logger logger = LoggerFactory.getLogger(GraphQLTest.class.getName());

	@Test
	public void testBasics() {

		RestAssured.basePath = "/structr/graphql";

		Group group      = null;
		Principal tester = null;

		try (final Tx tx = app.tx()) {

			final PropertyKey<List> membersKey = StructrApp.key(Group.class, "members");

			tester = app.create(Principal.class, new NodeAttribute<>(Principal.name, "tester"));
			group  = app.create(Group.class,
				new NodeAttribute<>(Group.name, "TestGroup"),
				new NodeAttribute<>(membersKey, Arrays.asList(tester))
			);

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
		}

		final String query1 = "{ Group { id, type, name, members { id, type, name } }, Principal(_pageSize: 1) { id, type name }}";
		final String query2 = "{ Group { id, type, name, members { } }}";
		final String query3 = "{ Group(id: \"" + group.getUuid() + "\") { id, type, name }}";

		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
				.filter(ResponseLoggingFilter.logResponseTo(System.out))
				.body(query1)

			.expect()
				.statusCode(200)
				.body("Group",                    hasSize(1))
				.body("Principal",                hasSize(1))
				.body("Group[0].id",              equalTo(group.getUuid()))
				.body("Group[0].type",            equalTo("Group"))
				.body("Group[0].name",            equalTo("TestGroup"))
				.body("Group[0].members[0].id",   equalTo(tester.getUuid()))
				.body("Group[0].members[0].type", equalTo("Principal"))
				.body("Group[0].members[0].name", equalTo("tester"))
				.body("Principal[0].id",          equalTo(group.getUuid()))
				.body("Principal[0].type",        equalTo("Group"))
				.body("Principal[0].name",        equalTo("TestGroup"))

			.when()
				.post("/");

		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
				.filter(ResponseLoggingFilter.logResponseTo(System.out))
				.body(query2)

			.expect()
				.statusCode(422)
				.body("message", equalTo("Parse error at } in line 1, column 36"))
				.body("code",    equalTo(422))
				.body("query",   equalTo(query2))

			.when()
				.post("/");

		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
				.filter(ResponseLoggingFilter.logResponseTo(System.out))
				.body(query3)

			.expect()
				.statusCode(200)
				.body("Group",                    hasSize(1))
				.body("Group[0].id",              equalTo(group.getUuid()))
				.body("Group[0].type",            equalTo("Group"))
				.body("Group[0].name",            equalTo("TestGroup"))

			.when()
				.post("/");
	}

	@Test
	public void testAdvancedQueries() {

		final List<MailTemplate> templates = new LinkedList<>();
		final List<Principal> team         = new LinkedList<>();
		Group group                        = null;

		try (final Tx tx = app.tx()) {

			final PropertyKey<List> membersKey = StructrApp.key(Group.class, "members");

			team.add(app.create(Principal.class, new NodeAttribute<>(Principal.name, "Axel")));
			team.add(app.create(Principal.class, new NodeAttribute<>(Principal.name, "Christian")));
			team.add(app.create(Principal.class, new NodeAttribute<>(Principal.name, "Christian")));
			team.add(app.create(Principal.class, new NodeAttribute<>(Principal.name, "Inès")));
			team.add(app.create(Principal.class, new NodeAttribute<>(Principal.name, "Kai")));
			team.add(app.create(Principal.class, new NodeAttribute<>(Principal.name, "Lukas")));
			team.add(app.create(Principal.class, new NodeAttribute<>(Principal.name, "Michael")));
			team.add(app.create(Principal.class, new NodeAttribute<>(Principal.name, "Susanne")));
			team.add(app.create(Principal.class, new NodeAttribute<>(Principal.name, "Tobias")));

			group  = app.create(Group.class,
				new NodeAttribute<>(Group.name, "Structr Team"),
				new NodeAttribute<>(membersKey, team)
			);

			app.create(Group.class,
				new NodeAttribute<>(Group.name, "All teams"),
				new NodeAttribute<>(membersKey, Arrays.asList(group))
			);

			templates.add(app.create(MailTemplate.class,
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "text"),   "MailTemplate4"),
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "locale"), "de_DE"),
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "name"),   "zrtsga"),
				new NodeAttribute<>(AbstractNode.owner, team.get(2))
			));

			templates.add(app.create(MailTemplate.class,
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "text"),   "MailTemplate2"),
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "locale"), "de_DE"),
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "name"),   "lertdf"),
				new NodeAttribute<>(AbstractNode.owner, team.get(0))
			));

			templates.add(app.create(MailTemplate.class,
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "text"),   "MailTemplate5"),
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "locale"), "de_DE"),
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "name"),   "tzegsg"),
				new NodeAttribute<>(AbstractNode.owner, team.get(3))
			));

			templates.add(app.create(MailTemplate.class,
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "text"),   "MailTemplate3"),
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "locale"), "de_DE"),
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "name"),   "asgw"),
				new NodeAttribute<>(AbstractNode.owner, team.get(1))
			));

			templates.add(app.create(MailTemplate.class,
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "text"),   "MailTemplate6"),
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "locale"), "de_DE"),
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "name"),   "dfjgr"),
				new NodeAttribute<>(AbstractNode.owner, team.get(4))
			));

			templates.add(app.create(MailTemplate.class,
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "text"),   "MailTemplate1"),
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "locale"), "de_DE"),
				new NodeAttribute<>(StructrApp.key(MailTemplate.class, "name"),   "abcdef"),
				new NodeAttribute<>(AbstractNode.owner, team.get(0))
			));


			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
		}

		RestAssured.basePath = "/structr/graphql";

		{
			final Map<String, Object> result = fetchGraphQL("{ Principal(id: \"" + team.get(0).getUuid() + "\") { id, type, name } }");
			assertMapPathValueIs(result, "Principal.#",      1);
			assertMapPathValueIs(result, "Principal.0.id",   team.get(0).getUuid());
			assertMapPathValueIs(result, "Principal.0.type", "Principal");
			assertMapPathValueIs(result, "Principal.0.name", "Axel");
		}

		{
			final Map<String, Object> result = fetchGraphQL("{ Principal(name: \"Axel\") { name } }");
			assertMapPathValueIs(result, "Principal.#",      1);
			assertMapPathValueIs(result, "Principal.0.name", "Axel");
			assertMapPathValueIs(result, "Principal.0.type", null);
			assertMapPathValueIs(result, "Principal.0.id",   null);
		}

		{
			final Map<String, Object> result = fetchGraphQL("{ Principal { name(_equals: \"Axel\") } }");
			assertMapPathValueIs(result, "Principal.#",      1);
			assertMapPathValueIs(result, "Principal.0.name", "Axel");
			assertMapPathValueIs(result, "Principal.0.type", null);
			assertMapPathValueIs(result, "Principal.0.id",   null);
		}

		{
			final Map<String, Object> result = fetchGraphQL("{ Principal { name(_contains: \"a\", _contains: \"l\", _conj: \"and\") } }");
			assertMapPathValueIs(result, "Principal.#",      4);
			assertMapPathValueIs(result, "Principal.0.name", "All teams");
			assertMapPathValueIs(result, "Principal.1.name", "Axel");
			assertMapPathValueIs(result, "Principal.2.name", "Lukas");
			assertMapPathValueIs(result, "Principal.3.name", "Michael");
		}

		{
			final Map<String, Object> result = fetchGraphQL("{ Group(_pageSize: 1) { name }, Principal(_pageSize: 2, _page: 2) { name(_contains: \"i\") } }");
			assertMapPathValueIs(result, "Group.#",          1);
			assertMapPathValueIs(result, "Group.0.name",     "All teams");
			assertMapPathValueIs(result, "Principal.#",      2);
			assertMapPathValueIs(result, "Principal.0.name", "Inès");
			assertMapPathValueIs(result, "Principal.1.name", "Kai");
		}

		{
			final Map<String, Object> result = fetchGraphQL("{ Group { name, members(_pageSize: 2, _page: 2) { name }}}");
			assertMapPathValueIs(result, "Group.#",                2);
			assertMapPathValueIs(result, "Group.0.name",           "All teams");
			assertMapPathValueIs(result, "Group.0.members",        new LinkedList<>());
			assertMapPathValueIs(result, "Group.1.name",           "Structr Team");
			assertMapPathValueIs(result, "Group.1.members.#",      2);
			assertMapPathValueIs(result, "Group.1.members.0.name", "Christian");
			assertMapPathValueIs(result, "Group.1.members.1.name", "Inès");
		}

		{
			final Map<String, Object> result = fetchGraphQL("{ Group { name, members(_sort: \"name\") { name }}}");
			assertMapPathValueIs(result, "Group.#",                2);
			assertMapPathValueIs(result, "Group.0.name",           "All teams");
			assertMapPathValueIs(result, "Group.0.members.#",      1);
			assertMapPathValueIs(result, "Group.1.name",           "Structr Team");
			assertMapPathValueIs(result, "Group.1.members.#",      9);
			assertMapPathValueIs(result, "Group.1.members.0.name", "Axel");
			assertMapPathValueIs(result, "Group.1.members.1.name", "Christian");
			assertMapPathValueIs(result, "Group.1.members.2.name", "Christian");
			assertMapPathValueIs(result, "Group.1.members.3.name", "Inès");
			assertMapPathValueIs(result, "Group.1.members.4.name", "Kai");
			assertMapPathValueIs(result, "Group.1.members.5.name", "Lukas");
			assertMapPathValueIs(result, "Group.1.members.6.name", "Michael");
			assertMapPathValueIs(result, "Group.1.members.7.name", "Susanne");
			assertMapPathValueIs(result, "Group.1.members.8.name", "Tobias");
		}

		{
			final Map<String, Object> result = fetchGraphQL("{ Group { name, members(_sort: \"name\", _desc: true) { name }}}");
			assertMapPathValueIs(result, "Group.#",                2);
			assertMapPathValueIs(result, "Group.0.name",           "All teams");
			assertMapPathValueIs(result, "Group.0.members.#",      1);
			assertMapPathValueIs(result, "Group.1.name",           "Structr Team");
			assertMapPathValueIs(result, "Group.1.members.#",      9);
			assertMapPathValueIs(result, "Group.1.members.8.name", "Axel");
			assertMapPathValueIs(result, "Group.1.members.7.name", "Christian");
			assertMapPathValueIs(result, "Group.1.members.6.name", "Christian");
			assertMapPathValueIs(result, "Group.1.members.5.name", "Inès");
			assertMapPathValueIs(result, "Group.1.members.4.name", "Kai");
			assertMapPathValueIs(result, "Group.1.members.3.name", "Lukas");
			assertMapPathValueIs(result, "Group.1.members.2.name", "Michael");
			assertMapPathValueIs(result, "Group.1.members.1.name", "Susanne");
			assertMapPathValueIs(result, "Group.1.members.0.name", "Tobias");
		}

		{
			final Map<String, Object> result = fetchGraphQL("{ Group { members { name(_contains: \"k\", _contains: \"l\", _conj: \"and\") }}}");
			assertMapPathValueIs(result, "Group.#",                2);
			assertMapPathValueIs(result, "Group.0.name",           null);
			assertMapPathValueIs(result, "Group.0.members",        new LinkedList<>());
			assertMapPathValueIs(result, "Group.1.members.0.name", "Lukas");
			assertMapPathValueIs(result, "Group.1.members.1",      null);
		}

		{
			final Map<String, Object> result = fetchGraphQL("{ Group { members { name(_contains: \"k\", _contains: \"l\", _conj: \"or\") }}}");
			assertMapPathValueIs(result, "Group.#",                2);
			assertMapPathValueIs(result, "Group.0.name",           null);
			assertMapPathValueIs(result, "Group.0.members",        new LinkedList<>());
			assertMapPathValueIs(result, "Group.1.members.#",      4);
			assertMapPathValueIs(result, "Group.1.members.0.name", "Axel");
			assertMapPathValueIs(result, "Group.1.members.1.name", "Kai");
			assertMapPathValueIs(result, "Group.1.members.2.name", "Lukas");
			assertMapPathValueIs(result, "Group.1.members.3.name", "Michael");
		}

		{
			final Map<String, Object> result = fetchGraphQL("{ MailTemplate { id, type, text(_contains: \"2\"), owner(_equals: { name: \"Axel\"}) { name } }}");
			assertMapPathValueIs(result, "MailTemplate.#",             1);
			assertMapPathValueIs(result, "MailTemplate.0.id",          templates.get(1).getUuid());
			assertMapPathValueIs(result, "MailTemplate.0.type",        "MailTemplate");
			assertMapPathValueIs(result, "MailTemplate.0.text",        "MailTemplate2");
			assertMapPathValueIs(result, "MailTemplate.0.name",        null);
			assertMapPathValueIs(result, "MailTemplate.0.owner.name",  "Axel");
		}

		{
			final Map<String, Object> result = fetchGraphQL("{ MailTemplate(_sort: \"owner.name\") { name, owner { name }}}");
			assertMapPathValueIs(result, "MailTemplate.#",                6);
			assertMapPathValueIs(result, "MailTemplate.0.name",           "abcdef");
			assertMapPathValueIs(result, "MailTemplate.0.owner.name",     "Axel");
			assertMapPathValueIs(result, "MailTemplate.1.name",           "lertdf");
			assertMapPathValueIs(result, "MailTemplate.1.owner.name",     "Axel");
			assertMapPathValueIs(result, "MailTemplate.2.name",           "asgw");
			assertMapPathValueIs(result, "MailTemplate.2.owner.name",     "Christian");
			assertMapPathValueIs(result, "MailTemplate.3.name",           "zrtsga");
			assertMapPathValueIs(result, "MailTemplate.3.owner.name",     "Christian");
			assertMapPathValueIs(result, "MailTemplate.4.name",           "tzegsg");
			assertMapPathValueIs(result, "MailTemplate.4.owner.name",     "Inès");
			assertMapPathValueIs(result, "MailTemplate.5.name",           "dfjgr");
			assertMapPathValueIs(result, "MailTemplate.5.owner.name",     "Kai");
		}

		{
			final Map<String, Object> result = fetchGraphQL("{ Group { name, members(_sort: \"name\", _desc: true) { name }}}");
			assertMapPathValueIs(result, "Group.#",                2);
			assertMapPathValueIs(result, "Group.0.name",           "All teams");
			assertMapPathValueIs(result, "Group.0.members.#",      1);
			assertMapPathValueIs(result, "Group.1.name",           "Structr Team");
			assertMapPathValueIs(result, "Group.1.members.#",      9);
			assertMapPathValueIs(result, "Group.1.members.8.name", "Axel");
			assertMapPathValueIs(result, "Group.1.members.7.name", "Christian");
			assertMapPathValueIs(result, "Group.1.members.6.name", "Christian");
			assertMapPathValueIs(result, "Group.1.members.5.name", "Inès");
			assertMapPathValueIs(result, "Group.1.members.4.name", "Kai");
			assertMapPathValueIs(result, "Group.1.members.3.name", "Lukas");
			assertMapPathValueIs(result, "Group.1.members.2.name", "Michael");
			assertMapPathValueIs(result, "Group.1.members.1.name", "Susanne");
			assertMapPathValueIs(result, "Group.1.members.0.name", "Tobias");
		}
	}

	@Test
	public void testSchema() {

		RestAssured.basePath = "/structr/graphql";

		RestAssured.given()

				.contentType("application/json; charset=UTF-8")
				.filter(ResponseLoggingFilter.logResponseTo(System.out))
				.body("{ __schema { types { name } }}")

			.expect()
				.statusCode(200)

			.when()
				.post("/");
	}

	@Test
	public void testMixedSchemaError() {

		RestAssured.basePath = "/structr/graphql";

		/*
		 * Structr uses two different methods of creating a GraphQL result, depending on the type of query. If a __schema query is
		 * sent, Structr delegates query execution to graphql-java, otherwise Structr uses its own query methods and only uses the
		 * structure information from graphql to control the output views on each level. Because of that, Structr cannot support
		 * GraphQL queries that request both schema and data information, and throws an exception.
		 */

		final String query1 = "{ __schema { types { name }}, Group { name }}";
		final String query2 = "{ Group { name }, __schema { types { name }} }";

		RestAssured.given()

				.contentType("application/json; charset=UTF-8")
				.body(query1)

			.expect()
				.statusCode(422)
				.body("message", equalTo("Unsupported query type, schema and data queries cannot be mixed."))
				.body("code",    equalTo(422))
				.body("query",   equalTo(query1))

			.when()
				.post("/");

		RestAssured.given()

				.contentType("application/json; charset=UTF-8")
				.body(query2)

			.expect()
				.statusCode(422)
				.body("message", equalTo("Unsupported query type, schema and data queries cannot be mixed."))
				.body("code",    equalTo(422))
				.body("query",   equalTo(query2))

			.when()
				.post("/");
	}

	@Test
	public void testGraphQLErrorMessages() {

		RestAssured.basePath = "/structr/graphql";

		final String query1 = "{ Group { id. type, name, members } }";
		final String query2 = "{ Group { id. type, name, owner } }";

		RestAssured.given()

				.filter(ResponseLoggingFilter.logResponseTo(System.out))
				.contentType("application/json; charset=UTF-8")
				.body(query1)

			.expect()
				.statusCode(422)
				.body("errors[0].message",             equalTo("Validation error of type SubSelectionRequired: Sub selection required for type Principal of field members"))
				.body("errors[0].locations[0].line",   equalTo(1))
				.body("errors[0].locations[0].column", equalTo(27))
				.body("errors[0].description",         equalTo("Sub selection required for type Principal of field members"))
				.body("errors[0].validationErrorType", equalTo("SubSelectionRequired"))

			.when()
				.post("/");

		RestAssured.given()

				.filter(ResponseLoggingFilter.logResponseTo(System.out))
				.contentType("application/json; charset=UTF-8")
				.body(query2)

			.expect()
				.statusCode(422)
				.body("errors[0].message",             equalTo("Validation error of type SubSelectionRequired: Sub selection required for type Principal of field owner"))
				.body("errors[0].locations[0].line",   equalTo(1))
				.body("errors[0].locations[0].column", equalTo(27))
				.body("errors[0].description",         equalTo("Sub selection required for type Principal of field owner"))
				.body("errors[0].validationErrorType", equalTo("SubSelectionRequired"))

			.when()
				.post("/");
	}

	@Test
	public void testFunctionPropertyTypeHint() {

		RestAssured.basePath = "/structr/graphql";

		List<NodeInterface> children = null;
		Principal user               = null;

		try (final Tx tx = app.tx()) {

			final JsonSchema schema             = StructrSchema.createFromDatabase(app);
			final JsonObjectType type           = schema.addType("Test");
			final JsonObjectType tmpType        = schema.addType("Tmp");

			type.relate(tmpType, "TMP", Relation.Cardinality.OneToMany, "parent", "children");

			type.addFunctionProperty("test1").setReadFunction("'test'").setTypeHint("String");
			type.addFunctionProperty("test2").setReadFunction("false").setTypeHint("Boolean");
			type.addFunctionProperty("test3").setReadFunction("int(42)").setTypeHint("Int");
			type.addFunctionProperty("test4").setReadFunction("12.34").setTypeHint("Double");
			type.addFunctionProperty("test5").setReadFunction("7465423674522").setTypeHint("Long");
			type.addFunctionProperty("test6").setReadFunction("this.owner").setTypeHint("Principal");
			type.addFunctionProperty("test7").setReadFunction("this.children").setTypeHint("Tmp[]");

			StructrSchema.replaceDatabaseSchema(app, schema);

			tx.success();

		} catch (URISyntaxException | FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// create test node
		try (final Tx tx = app.tx()) {

			user = app.create(Principal.class, "tester");

			final Class tmpType  = StructrApp.getConfiguration().getNodeEntityClass("Tmp");
			final Class testType = StructrApp.getConfiguration().getNodeEntityClass("Test");

			final PropertyKey nameKey     = StructrApp.getConfiguration().getPropertyKeyForJSONName(testType, "name");
			final PropertyKey ownerKey    = StructrApp.getConfiguration().getPropertyKeyForJSONName(testType, "owner");
			final PropertyKey childrenKey = StructrApp.getConfiguration().getPropertyKeyForJSONName(testType, "children");

			children = createTestNodes(tmpType, 10);

			app.create(testType,
				new NodeAttribute<>(nameKey, "Test"),
				new NodeAttribute<>(ownerKey, user),
				new NodeAttribute<>(childrenKey, children)
			);

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		final Map<String, Object> result = fetchGraphQL("{ Test { test1, test2, test3, test4, test5, test6 { id, type, name }, test7 { id, type } }}");
		assertMapPathValueIs(result, "Test.0.test1",        "test");
		assertMapPathValueIs(result, "Test.0.test2",        false);
		assertMapPathValueIs(result, "Test.0.test3",        42.0);
		assertMapPathValueIs(result, "Test.0.test4",        12.34);
		assertMapPathValueIs(result, "Test.0.test5",        7.465423674522E12);
		assertMapPathValueIs(result, "Test.0.test6.id",     user.getUuid());
		assertMapPathValueIs(result, "Test.0.test6.type",   "Principal");
		assertMapPathValueIs(result, "Test.0.test6.name",   "tester");
		assertMapPathValueIs(result, "Test.0.test7.#",      10);

		for (int i=0; i<10; i++) {
			assertMapPathValueIs(result, "Test.0.test7." + i + ".id",   children.get(i).getUuid());
			assertMapPathValueIs(result, "Test.0.test7." + i + ".type", "Tmp");
		}

		final String query = "{ Test { test6, test7 } }";

		RestAssured.given()

				.filter(ResponseLoggingFilter.logResponseTo(System.out))
				.contentType("application/json; charset=UTF-8")
				.body(query)

			.expect()
				.statusCode(422)
				.body("errors[0].message",             equalTo("Validation error of type SubSelectionRequired: Sub selection required for type Principal of field test6"))
				.body("errors[0].locations[0].line",   equalTo(1))
				.body("errors[0].locations[0].column", equalTo(10))
				.body("errors[0].description",         equalTo("Sub selection required for type Principal of field test6"))
				.body("errors[0].validationErrorType", equalTo("SubSelectionRequired"))
				.body("errors[1].message",             equalTo("Validation error of type SubSelectionRequired: Sub selection required for type Tmp of field test7"))
				.body("errors[1].locations[0].line",   equalTo(1))
				.body("errors[1].locations[0].column", equalTo(17))
				.body("errors[1].description",         equalTo("Sub selection required for type Tmp of field test7"))
				.body("errors[1].validationErrorType", equalTo("SubSelectionRequired"))

			.when()
				.post("/");
	}

	// ----- private methods -----
	private Map<String, Object> fetchGraphQL(final String query) {

		return RestAssured
			.given()
				.contentType("application/json; charset=UTF-8")
				.filter(ResponseLoggingFilter.logResponseTo(System.out))
				.body(query)

			.expect()
				.statusCode(200)

			.when()
				.post("/")

			.andReturn()
			.as(Map.class);
	}

	private void assertMapPathValueIs(final Map<String, Object> map, final String mapPath, final Object value) {

		final String[] parts = mapPath.split("[\\.]+");
		Object current       = map;

		for (int i=0; i<parts.length; i++) {

			final String part = parts[i];
			if (StringUtils.isNumeric(part)) {

				int index = Integer.valueOf(part);
				if (current instanceof List) {

					final List list = (List)current;
					if (index >= list.size()) {

						// value for nonexisting fields must be null
						assertEquals("Invalid map path result for " + mapPath, value, null);

						// nothing more to check here
						return;

					} else {

						current = list.get(index);
					}
				}

			} else if ("#".equals(part) && current instanceof List) {

				assertEquals("Invalid collection size for " + mapPath, value, ((List)current).size());

				// nothing more to check here
				return;

			} else {

				if (current instanceof Map) {

					current = ((Map)current).get(part);
				}
			}
		}

		assertEquals("Invalid map path result for " + mapPath, value, current);
	}
}
