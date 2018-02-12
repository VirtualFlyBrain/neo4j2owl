# OWL 2 EL <-> Neo4J Mapping "Direct existentials"

This is a preliminary draft of our Neo4J to OWL 2 mapping. The goal is to be able to import a well defined subset of OWL 2 EL ontologies into and export them from Neo4J in such a way that entailments and annotations are preserved (not however the syntactic structure) in the ontology after the round-trip. The main differences of this mapping to other mappings (see References below) are 
* its treatment of blank nodes in existential restrictions. Rather than creating blank nodes, we create direct edges between entities, labelled with the property of the existential restriction. This makes querying the graph more intuitive.
* its focus on implied knowledge (rather than syntactic representations). Rather than merely mapping an asserted axiom into Neo4J, we are interested in the following **`implied`** relationships:
  * **Class-Class**. For two class names A and B, an annotation property P, and an object property name R, we consider three types of relationships
    * SubClassOf restrictions of the form A SubClassOf: B
    * Existential restrictions of the form A SubClassOf: R some B
    * Annotation assertions of the form A Annotations: P B
  * **Individual-Individual**. For two individuals i and j, an annotation property P and an object property R, we consider
    * Object property assertions of the form i Facts: R j
    * Annotation assertions of the form i Annotations: P j 
  * **Class-Individual**. For a class A, an individual i, an annotation property P and an object property name R, we consider three types of relationships
    * Class assertions of the form i Types: A
    * Existential restrictions of the form i Types: R some B
    * Annotation assertions of the form C Annotations: P i 
  * **Individual-Class**. For a class A, an individual i, an annotation property P and an object property name R, we consider
    * Existential restrictions of the form A SubClassOf: R value i
    * Annotation assertions of the form i Annotations: P A    

The most similar mapping to our is the one used by [Monarch Initiatives SciGraph](https://github.com/SciGraph/SciGraph/wiki/MappingToOWL). The main differences are:
* In SciGraph, IRIs are first class citizens everywhere, while we prioritise safe labels to make query construction easier. This is especially important for edge types: Instead of MATCH p=()-[r:`http://purl.obolibrary.org/obo/BFO_0000050`]->() RETURN p LIMIT 25, we prefer to say MATCH p=()-[r:`obo:part_of`]->() RETURN p LIMIT 25
* Anonymous classes are kept alongside so called "convenience" edges, the latter of which correspond to the way we treat edges in general.


Some **ideosyncracies** of our approach are:
* To be able to roundtrip, we create disconnected nodes in the Neo4J graph representing properties so that we can represent metadata (such as labels or other annotations) pertaining to them. 
* We introduce a concept called 'safe qualified labels' (SL) that we materialise on all edges in the Neo4J graph, in order to make querying more intuitive. A safe qualified label has the form **ns:label** and is constructed as follows:
  * **ns:** corresponds to the namespace the relationship in question in question. This could be, for example, obo, representing the namespace http://purl.obolibrary.org/obo/.
  * **label** corresponds to the rdfs:label annotation of the relationship. If there is no rdfs:label annotation, label corresponds to the remainder of the entity IRI.
* An example use of an SL in Cypher is (:n)-['ro:part of']-(:x)
* Individuals are currently only typed with their most direct type
* We only support datatypes that are supported by both neo4j and OWL:
  * Integer/xsd:integer
  * String/xsd:string
  * Boolean/xsd:boolean
  * Float/xsd:float
* For properties, only annotations to literals are considered (not to other entities)

For readibility, we omit the neo4j2owl namespaces in the OWL 2 EL Axiom Column;

PREFIX: n2o: <http://neo4j2owl.org/mapping#>
Prefix: : <http://neo4j2owl.org/mapping#>

## Entities

All entities in the ontology, i.e. classes, individuals, object properties, data properties, and annotation properties are represented as nodes in the graph. Relationship nodes are only added to hold metadata about relations, and are disconnected from the rest of the graph. The iri, sl and short_form attributes on Neo4J nodes are the only three attributes that are not mapped into corresponding rdf statements. 

| Concept | OWL 2 EL Axiom | Neo4J Graph Pattern | Comment |
|---------------|---------------|----------------|----------------|
| Class declaration | Class: A | (:Class {iri: 'http://neo4j2owl.org/mapping#A', short_form:'A'}) |  |
| Individual declaration | Individual: i | (:Individual {iri: 'http://neo4j2owl.org/mapping#i'}) |  |
| Annotation property declaration | AnnotationProperty: R | (:AnnotationProperty {iri: 'http://neo4j2owl.org/mapping#R', short_form:'R', sl:'SL(R)'}) |  |
| Object property declaration | ObjectProperty: R | (:ObjectProperty {iri: 'http://neo4j2owl.org/mapping#R', short_form:'R', sl:'SL(R)'}) |  |
| Data property declaration | DataProperty: R | (:DataProperty {iri: 'http://neo4j2owl.org/mapping#R', short_form:'R', sl:'SL(R)'}) |  |


## Class-Class relationships

| Concept | OWL 2 EL Axiom | Neo4J Graph Pattern | Comment |
|---------------|---------------|----------------|----------------|
| SubClassOf | Class: A SubClassOf: B | (:Class {.. short_form:'A'..})-[r:SubClassOf]-(:Class {.. short_form:'B'..}) |  |
| Annotations on classes to other classes | Class: A Annotations: R B | (:Class {.. short_form:'A'..})-[r:`SL(R)`]-(:Class {.. short_form:'B'..}) |  |
| Simple existential "class" restrictions on classes | Class: A SubClassOf: R some B | (:Class {.. short_form:'A'..})-[r:`SL(R)`]-(:Class {.. short_form:'B'..}) |  |

## Class-Individual relationships

| Concept | OWL 2 EL Axiom | Neo4J Graph Pattern | Comment |
|---------------|---------------|----------------|----------------|
| Annotations on classes to individuals | Class: A Annotations: R i | (:Class {.. short_form:'A'..})-[r:`SL(R)`]-(:Individual {.. short_form:'i'..}) |  |
| Simple existential "individual" restrictions on classes | Class: A SubClassOf: R value i | (:Class {.. short_form:'A'..})-[r:`SL(R)`]-(:Individual {.. short_form:'i'..}) |  |

## Individual-Individual relationships

| Concept | OWL 2 EL Axiom | Neo4J Graph Pattern | Comment |
|---------------|---------------|----------------|----------------|
| Object Property Assertion | Individual: i Facts: R j | (:Individual {.. short_form:'i'..})-[r:`SL(R)`]-(:Individual {.. short_form:'j'..}) |  |
| Annotations on individuals to other individuals | Individual: i Annotations: R j | (:Individual {.. short_form:'i'..})-[r:`SL(R)`]-(:Individual {.. short_form:'j'..}) |  |

## Individual-Class relationships

| Concept | OWL 2 EL Axiom | Neo4J Graph Pattern | Comment |
|---------------|---------------|----------------|----------------|
| Class Assertion | Individual: i Types: A | (:Individual {.. short_form:'i'..})-[r:Types]-(:Class {.. short_form:'A'..}) |  |
| Simple existential restriction on assertion | Individual: i Types: R some A | (:Individual {.. short_form:'i'..})-[r:`SL(R)`]-(:Class {.. short_form:'A'..}) |  |
| Annotations on individuals to classes | Individual: i Annotations: R A | (:Individual {.. short_form:'i'..})-[r:`SL(R)`]-(:Class {.. short_form:'A'..}) |  |

## Entity-literal relationships
For reasons of feasibility, data property assertions or restrictions, will be incomplete in almost any implementation. In our reference implementation, we only consider asserted data property assertions.

| Concept | OWL 2 EL Axiom | Neo4J Graph Pattern | Comment |
|---------------|---------------|----------------|----------------|
| Annotations on classes to literals | Class: A Annotations: P "A"@en | (:Class {..,SF(R):'"A"@en'}) |  |
| Annotations on individuals to literals | Individual: i Annotations: P "A"@en | (:Individual {..,SF(R):'"A"@en'}) |  |
| Annotations on object properties to literals | ObjectProperty: R Annotations: P "A"@en | (:ObjectProperty {..,SF(R):'"A"@en'}) |  |
| Annotations on data properties to literals | DataProperty: R Annotations: P "A"@en | (:DataProperty {..,SF(R):'"A"@en'}) |  |
| Annotations on annotation properties to literals | AnnotationProperty: R Annotations: P "A"@en | (:AnnotationProperty {..,SF(R):'"A"@en'}) |  |
| Data property assertion | Individual: A Facts: R 2 | (:Individual {..,SF(R):2}) |  |
| Data property restriction | Class: A SubClassOf: R value 2 | (:Class {..,SF(R):2}) | |


## Future work
* Provenance should be added on edges only
* Add superproperties as additional edge types?

## References 

| Reference | Explanation |
| ---------------|----------------|
| [And Now for Something Completely Different: Using OWL with Neo4j](https://neo4j.com/blog/using-owl-with-neo4j/) | 2013 Blogpost on how OWL could be loaded into Neo. It provides a motivation for the conversion, and some code snippets to get started. |
| [SciGraph OWL2Neo](https://github.com/SciGraph/SciGraph/wiki/Neo4jMapping) | Preliminary mapping |
| [SciGraph Neo2OWL](https://github.com/SciGraph/SciGraph/wiki/MappingToOWL) | Preliminary mapping |
| [Convert OWL to labeled property graph and import into Neo4J](https://github.com/flekschas/owl2neo4j) | Covers only [class hierarchy/and annotations](https://github.com/flekschas/owl2neo4j/wiki/How-is-OWL-translated-into-a-labeled-property-graph%3F).   |
| [Neo4J-Jena Wrapper](https://github.com/semr/neo4jena) | Provides the mapping of RDF to property graphs (Neo4J) using Jena API. Literals are represented as nodes. |
| [Sail Ouplementation](https://github.com/tinkerpop/blueprints/wiki/Sail-Ouplementation) | Interface to [access property graphs directly] as a triple store. No details on mappings in documentation. |
| [Using Neo4J to load and query OWL ontologies](http://sujitpal.blogspot.co.uk/2009/05/using-neo4j-to-load-and-query-owl.html) | 2009 blogpost with ad-hoc implementation, showing how to load the wine ontology with Jena and transform it into Neo4J. The mapping is intuitive and RDF-like, translating triples directly to nodes and edges. |
| [Importing RDF data into Neo4J](Using Neo4J to load and query OWL ontologies) | 2016 blogpost defining a mapping proposition. Annotations are added on nodes if they are literatels, else nodes are created (good). Blank nodes are created. [Neo4J stored procedure](https://github.com/jbarrasa/neosemantics) exists. |
| [Building a semantic graph in Neo4j](https://jesusbarrasa.wordpress.com/2016/04/06/building-a-semantic-graph-in-neo4j/) | 2016 blogpost on how to define an RDFS style ontology in Neo4J. The author has a keen interest in RDF/OWL2Neo4J mappings, his entire blog seems to be mainly about that topic. |
| [Neo4j is your RDF store (series)](https://jesusbarrasa.wordpress.com/2016/11/17/neo4j-is-your-rdf-store-part-1/) | Interesting series describing how to use Neo as a triple store. | 
| [Storing and querying RDF in Neo4j](http://www.snee.com/bobdc.blog/2014/01/storing-and-querying-rdf-in-ne.html) | Description of a SPARQL plugin to query Neo4J based on Sail Ouplementation. |
| [Importing ttl (Turtle) ontologies in Neo4j](http://michaelbloggs.blogspot.co.uk/2013/05/importing-ttl-turtle-ontologies-in-neo4j.html) | 2013 blogpost with code on how to import ttl into neo using Sesame API |
| [OLS: OWL to Neo4j schema](https://www.slideshare.net/thesimonjupp/ontologies-neo4jgraphworkshopberlin) | Largely undocumented, but see slide 16 following of Berlin workshop. Implementing our mapping partially. | 


