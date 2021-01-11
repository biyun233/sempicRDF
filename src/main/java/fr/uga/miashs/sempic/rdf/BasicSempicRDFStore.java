/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.uga.miashs.sempic.rdf;



import fr.uga.miashs.sempic.model.rdf.SempicOnto;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;

import java.util.List;

/**
 * Classe offrant les services de base de l'appli sempic : créer une photo,
 * lire les annotations d'une photo, et effacer les annotations d'une photo
 * @author Jerome David <jerome.david@univ-grenoble-alpes.fr>
 */

public class BasicSempicRDFStore extends RDFStore {
    
    public final PrefixMapping prefixes;

    public BasicSempicRDFStore() {
        prefixes = PrefixMapping.Factory.create();
        prefixes.withDefaultMappings(PrefixMapping.Standard);
        prefixes.setNsPrefix("sempic", SempicOnto.NS);
    }
    
    public Resource createPhoto(long photoId, long albumId, long ownerId) {
        // create an empty RDF graph
        Model m = ModelFactory.createDefaultModel();
        // create an instance of Photo in Model m
        Resource pRes = m.createResource(Namespaces.getPhotoUri(photoId), SempicOnto.Picture);

        pRes.addLiteral(SempicOnto.albumId, albumId);
        pRes.addLiteral(SempicOnto.ownerId, ownerId);


        saveModel(m);

        return pRes;
    }

    public void deletePhoto(long photoId) {
        // create an instance of Photo in Model m
        Resource pRes = ResourceFactory.createResource(Namespaces.getPhotoUri(photoId));
        deleteResource(pRes);
    }

    /**
     * Query a Photo and retrieve all the direct properties of the photo and if
     * the property are depic, takenIn or takenBy, it also retrieve the labels
     * of the object of these properties
     *
     * @param photoId
     * @return
     */
    public Resource readPhoto(long photoId) {
        String pUri = Namespaces.getPhotoUri(photoId);
        ParameterizedSparqlString pss = new ParameterizedSparqlString(prefixes);
        pss.setBaseUri(Namespaces.photoNS);
        
        pss.setCommandText(
                "CONSTRUCT {"
                        + "?photo ?p ?o ."
                        + "?photo ?p1 ?o1 ."
                        + "?o1 rdfs:label ?o2 ."
                + "} WHERE { "
                        + "?photo ?p ?o ."
                        + "OPTIONAL {"
                        + "?photo ?p1 ?o1 ."
                        + "?o1 rdfs:label ?o2 ."
                        + "FILTER (?p1 IN (<" + SempicOnto.depicts + ">,<" + SempicOnto.location + ">,<" + SempicOnto.hasAuthor + ">,<"  + SempicOnto.wasTakenFor + ">,<" + SempicOnto.title + ">)) "
                        +"}"
                 + "}");
        pss.setIri("photo", pUri);
        
        Model m = cnx.queryConstruct(pss.asQuery());
        return m.getResource(pUri);
    }

    public void deleteAnnotation(Resource picture, Property p, Resource o){
        cnx.begin(ReadWrite.WRITE);
        if(o.isAnon()){
            cnx.update("DELETE WHERE { "
            + "<" + picture.getURI() + "> <" + p.getURI() + "> ?o . "
            + "?o <" + RDFS.label + "> \"" + o.getProperty(RDFS.label).getString() + "\" ."
            + "?o ?p ?x}");
        } else {
            cnx.update("DELETE DATA { <" + picture.getURI() + "> <" + p.getURI() + "> <" + o.getURI() + "> .");
        }
        cnx.commit();
        if(picture.getModel() != null){
            picture.getModel().removeAll(picture,p,o);
        }
    }
    public void deleteAnnotation(Resource picture, Property p){
        cnx.update("DELETE WHERE { <" + picture.getURI() + "> <" + p.getURI() + "> ?o }");
        if(picture.getModel() != null){
            picture.removeAll(p);
        }
    }

    public void addAnnotation(Resource picture, Property p, Resource o) {
        if(o == null){
            return;
        }
        Model m = ModelFactory.createDefaultModel();
        if(o.getModel() != null){
            m.add(o.listProperties());
        }
        m.add(picture,p,o);
        cnx.begin(ReadWrite.WRITE);
        cnx.load(m);
        cnx.commit();
        picture.getModel().add(m);
    }

    public void setAnnotation(Resource picture, Property property, Resource r){
        deleteAnnotation(picture,property);
        addAnnotation(picture,property,r);
    }

    public List<Resource> lookupInstances(Resource type, String labelContent, Resource instancietedProperty, long ownerId){
        String query = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
                + "PREFIX text: <http://jena.apache.org/text#> "
                + "CONSTRUCT {?s rdfs:label ?l} "
                + "WHERE { ?s rdfs:label ?l.";
        if(type != null){
            query += "?s a <" + type.getURI() + "> .";
        }
        if(instancietedProperty != null){
            query += "[ <" + instancietedProperty.getURI() + "> ?s";
            if(ownerId != -1){
                query += "; <" + SempicOnto.ownerId + "> " + ownerId;
            }
            query += "] .";
        }
        if(instancietedProperty != null & !"".equals(labelContent)){
            query += "FILTER regex(?l, \"" + labelContent.toLowerCase() + "\", \"i\")";
            query += "(?s) text:query (rdfs:label \"" + labelContent.toLowerCase() + "*\") .";
        }
        query += "FILTER (ISIRI(?S)) .";
        query += "}";
        Model m = cnx.queryConstruct(query);
        List<Resource> res = m.listSubjects().toList();
        return res;
    }
}
