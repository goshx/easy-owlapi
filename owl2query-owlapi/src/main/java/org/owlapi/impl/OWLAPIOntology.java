package org.owlapi.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectInverseOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChangeException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.model.OWLPropertyExpression;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;

import cz.cvut.kbss.owl2query.model.Hierarchy;
import cz.cvut.kbss.owl2query.model.InternalReasonerException;
import cz.cvut.kbss.owl2query.model.OWL2Ontology;
import cz.cvut.kbss.owl2query.model.OWL2QueryException;
import cz.cvut.kbss.owl2query.model.OWL2QueryFactory;
import cz.cvut.kbss.owl2query.model.OWLObjectType;
import cz.cvut.kbss.owl2query.model.SizeEstimate;
import cz.cvut.kbss.owl2query.model.SizeEstimateImpl;
import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLIndividualAxiom;
import org.semanticweb.owlapi.model.OWLLogicalAxiom;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.util.OWLEntityRemover;

public class OWLAPIOntology implements OWL2Ontology<OWLObject> {

    private static final Logger LOG = Logger
            .getLogger(OWLAPIOntology.class.getName());

    private OWLOntology o;
    private OWLOntologyManager m;
    private OWLDataFactory f;
    private OWLReasoner r;
    private OWL2QueryFactory<OWLObject> factory;
    private SizeEstimate<OWLObject> sizeEstimate;
    private OWLReasoner structuralReasoner;

    private File file;

    public OWLAPIOntology(String owlFilePath) {
        this.file = new File(owlFilePath);
        this.m = OWLManager.createOWLOntologyManager();

        try {
            this.o = m.loadOntologyFromOntologyDocument(this.file);
        } catch (Exception e) {
            throw new InternalReasonerException();
        }

        this.f = m.getOWLDataFactory();
        this.r = new StructuralReasonerFactory().createReasoner(o);

        structuralReasoner = new StructuralReasonerFactory().createReasoner(o);
        structuralReasoner.precomputeInferences(InferenceType.values());
        factory = new OWLAPIFactory(m, o);

        sizeEstimate = new SizeEstimateImpl<OWLObject>(this);
    }
    
    private boolean applyChange(OWLOntologyChange oc) {
        try {
            m.applyChange(oc);
            m.saveOntology(o);
            return true;
        } catch (OWLOntologyStorageException ex) {
            return false;
        }
    }
    
    public boolean applyChanges(List<? extends OWLOntologyChange> ocs) {
        try {
            m.applyChanges(ocs);
            m.saveOntology(o);
            return true;
        } catch (OWLOntologyStorageException ex) {
            return false;
        }
    }
    
    public boolean setIndividualForClass(final OWLObject in, final  OWLObject cl) {
        OWLClassExpression c = asOWLClassExpression(cl);
        OWLIndividual i = (OWLIndividual)in;
	OWLAxiom axiom = f.getOWLClassAssertionAxiom(c, i);
        AddAxiom addAxiom = new AddAxiom(o, axiom);
	return applyChange(addAxiom);
    }
    
    public boolean setPropertyValueForIndividual(final OWLObject in, 
            final  OWLObject pp, final OWLObject vl) {
        OWLNamedIndividual i = (OWLNamedIndividual)in;
        if(is(pp, OWLObjectType.OWLObjectProperty)) {
            OWLObjectPropertyExpression c 
                    = (OWLObjectPropertyExpression)pp;
            OWLAxiom axiom = f.getOWLObjectPropertyAssertionAxiom(c, i, (OWLIndividual)vl);
            AddAxiom addAxiom = new AddAxiom(o, axiom);
            return applyChange(addAxiom);
        } else {
            OWLDataPropertyExpression c 
                    = (OWLDataPropertyExpression)asOWLPropertyExpression(pp);
            OWLLiteral v = (OWLLiteral)vl;
            OWLAxiom axiom = f.getOWLDataPropertyAssertionAxiom(c, i, (OWLLiteral)vl);
            AddAxiom addAxiom = new AddAxiom(o, axiom);
            return applyChange(addAxiom);
        }
    }
    
    public boolean setSubClass(final OWLObject sub
            , final OWLObject sup) {
        OWLClass bc = (OWLClass)sub;
        OWLClass pc = (OWLClass)sup;
        OWLAxiom axiom = f.getOWLSubClassOfAxiom(bc, pc);
        AddAxiom addAxiom = new AddAxiom(o, axiom);
        return applyChange(addAxiom);
    }
    
    public boolean setSubDataProperty(final OWLObject sub
            , final OWLObject sup) {
        OWLDataProperty bc = (OWLDataProperty)sub;
        OWLDataProperty pc = (OWLDataProperty)sup;
        OWLAxiom axiom = f.getOWLSubDataPropertyOfAxiom(bc, pc);
        AddAxiom addAxiom = new AddAxiom(o, axiom);
        return applyChange(addAxiom);
    }
    
    public boolean setSubObjectProperty(final OWLObject sub
            , final OWLObject sup) {
        OWLObjectProperty bc = (OWLObjectProperty)sub;
        OWLObjectProperty pc = (OWLObjectProperty)sup;
        OWLAxiom axiom = f.getOWLSubObjectPropertyOfAxiom(bc, pc);
        AddAxiom addAxiom = new AddAxiom(o, axiom);
        return applyChange(addAxiom);
    }
    
    public boolean removeOWLClass(final OWLObject e) {
        OWLEntityRemover rm = new OWLEntityRemover(m.getOntologies());
        OWLClass cl = (OWLClass)e;
        cl.accept(rm);
        return applyChanges(rm.getChanges());
    }
    
    public boolean removeOWLDataProperty(final OWLObject e) {
        OWLEntityRemover rm = new OWLEntityRemover(m.getOntologies());
        OWLDataProperty cl = (OWLDataProperty)e;
        cl.accept(rm);
        return applyChanges(rm.getChanges());
    }
    
    public boolean removeOWLIndividual(final OWLObject e) {
        OWLEntityRemover rm = new OWLEntityRemover(m.getOntologies());
        OWLNamedIndividual cl = (OWLNamedIndividual)e;
        cl.accept(rm);
        return applyChanges(rm.getChanges());
    }
    
    public boolean removeOWLObjectProperty(final OWLObject e) {
        OWLEntityRemover rm = new OWLEntityRemover(m.getOntologies());
        OWLObjectProperty cl = (OWLObjectProperty)e;
        cl.accept(rm);
        return applyChanges(rm.getChanges());
    }
    
    public boolean removeTypeOfIndividual(final OWLObject tp, final OWLObject in) {
        OWLClassExpression c = asOWLClassExpression(tp);
        OWLIndividual i = (OWLIndividual)in;
	OWLAxiom axiom = f.getOWLClassAssertionAxiom(c, i);
        RemoveAxiom rmAxiom = new RemoveAxiom(o, axiom);
	return applyChange(rmAxiom);
    }
    
    public boolean removePropertyForIndividual(final OWLObject in, 
            final  OWLObject pp, final OWLObject vl) {
        OWLNamedIndividual i = (OWLNamedIndividual)in;
        if(is(pp, OWLObjectType.OWLObjectProperty)) {
            OWLObjectPropertyExpression c 
                    = (OWLObjectPropertyExpression)asOWLPropertyExpression(pp);
            OWLAxiom axiom = f.getOWLObjectPropertyAssertionAxiom(c, i, (OWLIndividual)vl);
            RemoveAxiom addAxiom = new RemoveAxiom(o, axiom);
            return applyChange(addAxiom);
        } else {
            OWLDataPropertyExpression c 
                    = (OWLDataPropertyExpression)asOWLPropertyExpression(pp);
            OWLLiteral v = (OWLLiteral)vl;
            OWLAxiom axiom = f.getOWLDataPropertyAssertionAxiom(c, i, (OWLLiteral)vl);
            RemoveAxiom addAxiom = new RemoveAxiom(o, axiom);
            return applyChange(addAxiom);
        }
    }

    public OWLObject asOWLObject(String e) {
        Matcher m1 = Pattern
                .compile("^http\\:\\/\\/.+$")
                .matcher(e);
        Matcher m2 = Pattern
                .compile("(?<=[<>])([^<>].*?)(?=[<>])")
                .matcher(e);

        if (!m1.find(0) && !m2.find(0)) {
            OWLOntologyID oId = o.getOntologyID();
            if(!oId.isAnonymous())
                e = oId.getOntologyIRI().get().toString() + "#" + e;
        } else if (m2.find(0)) {
            e = m2.group();
        }

        IRI iri = IRI.create(e);
        OWLObject obj = f.getOWLClass(iri);

        if (is(obj, OWLObjectType.OWLClass)) {
            return f.getOWLClass(iri);
        } else if (is(obj, OWLObjectType.OWLDataProperty)) {
            return f.getOWLDataProperty(iri);
        } else if (is(obj, OWLObjectType.OWLObjectProperty)) {
            return f.getOWLObjectProperty(iri);
        } else if (is(obj, OWLObjectType.OWLAnnotationProperty)) {
            return f.getOWLAnnotationProperty(iri);
        } else {
            return f.getOWLNamedIndividual(iri);
        }
    }
    
    public OWLObject asOWLObject(String e, OWLObjectType t) {
        if(t.equals(OWLObjectType.OWLLiteral))
            return getFactory().literal(e);
        
        Matcher m1 = Pattern
                .compile("^http\\:\\/\\/.+$")
                .matcher(e);
        Matcher m2 = Pattern
                .compile("(?<=[<>])([^<>].*?)(?=[<>])")
                .matcher(e);

        if (!m1.find(0) && !m2.find(0)) {
            OWLOntologyID oId = o.getOntologyID();
            if(!oId.isAnonymous())
                e = oId.getOntologyIRI().get().toString() + "#" + e;
        } else if (m2.find(0)) {
            e = m2.group();
        }

        IRI iri = IRI.create(e);
        
        if (t.equals(OWLObjectType.OWLClass)) {
            return f.getOWLClass(iri);
        } else if (t.equals(OWLObjectType.OWLDataProperty)) {
            return f.getOWLDataProperty(iri);
        } else if (t.equals(OWLObjectType.OWLObjectProperty)) {
            return f.getOWLObjectProperty(iri);
        } else if (t.equals(OWLObjectType.OWLAnnotationProperty)) {
            return f.getOWLAnnotationProperty(iri);
        } else {
            return f.getOWLNamedIndividual(iri);
        } 
    }

    private OWLNamedIndividual asOWLNamedIndividual(final OWLObject e) {
        if (e instanceof OWLNamedIndividual) {
            return (OWLNamedIndividual) e;
        } else if (e instanceof OWLEntity) {
            return f.getOWLNamedIndividual(((OWLEntity) e).getIRI());
        } else {
            throw new InternalReasonerException();
        }
    }

    private OWLLiteral asOWLLiteral(final OWLObject e) {
        if (e instanceof OWLLiteral) {
            return (OWLLiteral) e;
        } else {
            throw new InternalReasonerException();
        }
    }

    private OWLClassExpression asOWLClassExpression(final OWLObject e) {
        if (e instanceof OWLClassExpression) {
            return (OWLClassExpression) e;
        } else if (e instanceof OWLEntity) {
            final OWLEntity ee = (OWLEntity) e;
            // if (o.containsClassInSignature(ee.getIRI())) {
            return f.getOWLClass(ee.getIRI());
            // }
        }

        return null;
    }

    private OWLPropertyExpression asOWLPropertyExpression(
            final OWLObject e) {
        if (e instanceof OWLEntity) {
            final OWLEntity ee = (OWLEntity) e;
            if (is(ee, OWLObjectType.OWLDataProperty)) {
                return f.getOWLDataProperty(ee.getIRI());
            } else {
                return f.getOWLObjectProperty(ee.getIRI());
            }
        } else if (e instanceof OWLPropertyExpression) {
            return (OWLPropertyExpression) e;
        }
        throw new IllegalArgumentException();
    }

    private OWLObjectProperty asOWLObjectProperty(final OWLObject e) {
        if (e instanceof OWLObjectProperty) {
            return (OWLObjectProperty) e;
        } else if (e instanceof OWLEntity) {
            final OWLEntity ee = (OWLEntity) e;
            if (is(ee, OWLObjectType.OWLObjectProperty)) {
                return f.getOWLObjectProperty(ee.getIRI());
            }
        }
        return null;
    }
    
    public Set<OWLObject> getPropertiesForOWLObject(OWLObject i, 
            OWLObjectType t) {
        Set<OWLObject> set = new HashSet<>();
        OWLNamedIndividual in = asOWLNamedIndividual(i);
        Set<OWLIndividualAxiom> axiomSet=o.getAxioms(in, Imports.INCLUDED);
        for (OWLLogicalAxiom ax : axiomSet) {
            if(!ax.getIndividualsInSignature().isEmpty()){
                if(t.equals(OWLObjectType.OWLDataProperty))
                    set.addAll(ax.getDataPropertiesInSignature());
                else if(t.equals(OWLObjectType.OWLObjectProperty))
                    set.addAll(ax.getObjectPropertiesInSignature());
                else if(t.equals(OWLObjectType.OWLAnnotationProperty))
                    set.addAll(ax.getAnnotationPropertiesInSignature());
                else if(t.equals(OWLObjectType.OWLClass))
                    set.addAll(ax.getClassesInSignature());
                else
                    set.addAll(ax.getIndividualsInSignature());
            }
        }
        return set;
    }

    @Override
    public Set<OWLClass> getClasses() {
        Set<OWLClass> set = new HashSet<OWLClass>(
                o.getClassesInSignature(Imports.INCLUDED));
        set.add(f.getOWLThing());
        set.add(f.getOWLNothing());
        return set;
    }

    @Override
    public Set<OWLObjectProperty> getObjectProperties() {
        final Set<OWLObjectProperty> set = o
                .getObjectPropertiesInSignature(Imports.INCLUDED);
        set.add(f.getOWLBottomObjectProperty());
        set.add(f.getOWLTopObjectProperty());
        return set;
    }

    @Override
    public Set<OWLDataProperty> getDataProperties() {
        final Set<OWLDataProperty> set = o
                .getDataPropertiesInSignature(Imports.INCLUDED);
        set.add(f.getOWLBottomDataProperty());
        set.add(f.getOWLTopDataProperty());
        return set;
    }

    @Override
    public Set<OWLNamedIndividual> getIndividuals() {
        return o.getIndividualsInSignature(Imports.INCLUDED);
    }

    public Set<OWLLiteral> getLiterals() {
        Set<OWLLiteral> set = new HashSet<OWLLiteral>();
        for (OWLIndividual i : getIndividuals()) {
            for (OWLDataPropertyAssertionAxiom ax : o
                    .getDataPropertyAssertionAxioms(i)) {
                set.add(ax.getObject());
            }
        }
        return set;
    }

    @Override
    public Set<? extends OWLObject> getDifferents(OWLObject i) {
        if (!(i instanceof OWLEntity)) {
            throw new InternalReasonerException();
        }

        return r.getDifferentIndividuals(asOWLNamedIndividual(i))
                .getFlattened();
    }

    @Override
    public Set<? extends OWLObject> getDomains(OWLObject pred) {
        final OWLPropertyExpression ope = asOWLPropertyExpression(pred);

        if (ope != null) {
            if (ope.isAnonymous()) {
                throw new InternalReasonerException();
            } else if (ope.isObjectPropertyExpression()) {
                return r.getObjectPropertyDomains(
                        ((OWLEntity) ope).asOWLObjectProperty(), true).getFlattened(); // TODO
            } else if (ope.isDataPropertyExpression()) {
                return r.getDataPropertyDomains(((OWLEntity) ope).asOWLDataProperty(),
                        true).getFlattened(); // TODO
            }
        }

        throw new InternalReasonerException();
    }

    public Set<? extends OWLObject> getEquivalentClasses(OWLObject ce) {
        final OWLClassExpression c = asOWLClassExpression(ce);

        return r.getEquivalentClasses(c).getEntities();
    }

    @Override
    public Set<? extends OWLObject> getInverses(OWLObject ope) {
        final OWLPropertyExpression opex = asOWLPropertyExpression(ope);

        if (opex.isObjectPropertyExpression()) {

            if (opex.isAnonymous()) {
                return r.getEquivalentObjectProperties(
                        ((OWLObjectPropertyExpression) opex).getNamedProperty())
                        .getEntities();
            } else {
                return r.getInverseObjectProperties(
                        ((OWLObjectPropertyExpression) opex).getNamedProperty())
                        .getEntities();
            }
        }
        throw new InternalReasonerException();
    }

    @Override
    public Set<? extends OWLObject> getRanges(OWLObject pred) {
        final OWLPropertyExpression ope = asOWLPropertyExpression(pred);

        if (ope != null) {
            if (ope.isAnonymous()) {
                throw new InternalReasonerException();
            } else if (ope.isObjectPropertyExpression()) {
                return r.getObjectPropertyRanges(
                        ((OWLEntity) ope).asOWLObjectProperty(), true)
                        .getFlattened(); // TODO
            } else if (ope.isDataPropertyExpression()) {
                // return r.getDataPropertyRanges(((OWLEntity)
                // ope).asOWLDataProperty());
                return new HashSet<>(EntitySearcher.getRanges(
                        ((OWLEntity) ope).asOWLDataProperty(), o));
                // TODO
            }
        }
        throw new InternalReasonerException();
    }

    @Override
    public Set<? extends OWLObject> getSames(OWLObject i) {
        if (!(i instanceof OWLEntity)) {
            throw new InternalReasonerException();
        }

        return r.getSameIndividuals(asOWLNamedIndividual(i))
                .getEntities();
    }

    @Override
    public Set<OWLClass> getTypes(OWLObject i, boolean direct) {
        if (!(i instanceof OWLEntity)) {
            throw new InternalReasonerException();
        }

        return r.getTypes(asOWLNamedIndividual(i), direct).getFlattened();
    }

    @Override
    public boolean is(OWLObject e, final OWLObjectType... tt) {
        boolean result = false;

        for (final OWLObjectType t : tt) {
            switch (t) {
                case OWLLiteral:
                    result = e instanceof OWLLiteral;
                    break;
                case OWLAnnotationProperty:
                    if (e instanceof OWLEntity) {
                        result = o.containsAnnotationPropertyInSignature(
                                ((OWLEntity) e).getIRI(), Imports.INCLUDED);
                    }
                    break;
                case OWLDataProperty:
                    if (e instanceof OWLEntity) {
                        result = o.containsDataPropertyInSignature(
                                ((OWLEntity) e).getIRI(), Imports.INCLUDED)
                                || e.equals(f.getOWLTopDataProperty())
                                || e.equals(f.getOWLBottomDataProperty());
                    }
                    break;
                case OWLObjectProperty:
                    if (e instanceof OWLEntity) {
                        result = o.containsObjectPropertyInSignature(
                                ((OWLEntity) e).getIRI(), Imports.INCLUDED)
                                || e.equals(f.getOWLTopObjectProperty()) || e
                                .equals(f.getOWLBottomObjectProperty());
                    }
                    break;
                case OWLClass:
                    if (e instanceof OWLEntity) {
                        result = o.containsClassInSignature(
                                ((OWLEntity) e).getIRI(), Imports.INCLUDED)
                                || e.equals(f.getOWLThing())
                                || e.equals(f.getOWLNothing());
                    }
                    break;
                case OWLNamedIndividual:
                    if (e instanceof OWLEntity) {
                        result = o.containsIndividualInSignature(
                                ((OWLEntity) e).getIRI(), Imports.INCLUDED);
                    }

                    break;
                default:
                    break;
            }
            if (result) {
                break;
            }
        }

        return result;
    }

    @Override
    public boolean isSameAs(OWLObject i1, OWLObject i2) {
        final OWLIndividual ii1 = asOWLNamedIndividual(i1);
        final OWLIndividual ii2 = asOWLNamedIndividual(i2);

        if (i1.equals(i2)) {
            return true;
        }

        return r.isEntailed(f.getOWLSameIndividualAxiom(ii1, ii2));
    }

    @Override
    public boolean isDifferentFrom(OWLObject i1, OWLObject i2) {
        if (!(i1 instanceof OWLEntity) || !(i2 instanceof OWLEntity)) {
            throw new InternalReasonerException();
        }

        return r.isEntailed(f.getOWLDifferentIndividualsAxiom(
                asOWLNamedIndividual(i1),
                asOWLNamedIndividual(i2)));
    }

    @Override
    public boolean isTypeOf(OWLObject ce, OWLObject i, boolean direct) {

        if (is(i, OWLObjectType.OWLLiteral)) {
            return false;
        }

        final OWLNamedIndividual ii = asOWLNamedIndividual(i);
        final OWLClassExpression cce = asOWLClassExpression(ce);

        if (direct) {
            return r.getInstances(cce, true).containsEntity(ii);
        } else {
            return r.isEntailed(f.getOWLClassAssertionAxiom(cce, ii));
        }
    }

    @Override
    public void ensureConsistency() {
        if (LOG.isLoggable(Level.CONFIG)) {
            LOG.config("Ensure consistency");
        }

        if (LOG.isLoggable(Level.CONFIG)) {
            LOG.config("	* isConsistent ?");
        }
        if (!r.isConsistent()) {
            throw new InternalReasonerException();
        }
        if (LOG.isLoggable(Level.CONFIG)) {
            LOG.config("	* true");
        }
    }

    @Override
    public Set<? extends OWLObject> getIndividualsWithProperty(OWLObject pvP,
            OWLObject pvIL) {
        final OWLPropertyExpression pex = asOWLPropertyExpression(pvP);

        final Set<OWLObject> set = new HashSet<OWLObject>();

        if (pex != null) {
            if (pex.isObjectPropertyExpression()) {
                if (!is(pvIL, OWLObjectType.OWLNamedIndividual)) {
                    return set;
                }

                final OWLNamedIndividual object = asOWLNamedIndividual(pvIL);

                for (final OWLNamedIndividual i : getIndividuals()) {
                    if (r.isEntailed(f.getOWLObjectPropertyAssertionAxiom(
                            (OWLObjectPropertyExpression) pex, i, object))) {
                        set.add(i);
                    }
                }
            } else if (pex.isDataPropertyExpression()) {
                if (!is(pvIL, OWLObjectType.OWLLiteral)) {
                    return set;
                }

                final OWLLiteral object = asOWLLiteral(pvIL);

                for (final OWLNamedIndividual i : getIndividuals()) {
                    if (r.isEntailed(f.getOWLDataPropertyAssertionAxiom(
                            (OWLDataPropertyExpression) pex, i, object))) {
                        set.add(i);
                    }
                }
            }
        }

        return set;
    }

    @Override
    public Set<? extends OWLObject> getPropertyValues(OWLObject pvP,
            OWLObject pvI) {
        final OWLPropertyExpression pex = asOWLPropertyExpression(pvP);
        final OWLNamedIndividual ni = asOWLNamedIndividual(pvI);

        if (pex != null) {
            if (pex.isObjectPropertyExpression()) {

                if (pex.isOWLTopObjectProperty()) {
                    return getIndividuals();
                } else {
                    return r.getObjectPropertyValues(ni,
                            (OWLObjectPropertyExpression) pex).getFlattened();
                }
            } else if (pex.isDataPropertyExpression()) {
                if (pex.isOWLTopDataProperty()) {
                    return getLiterals();
                } else {
                    return r.getDataPropertyValues(ni, (OWLDataProperty) pex);
                }
            }
        }
        throw new IllegalArgumentException();
    }

    @Override
    public SizeEstimate<OWLObject> getSizeEstimate() {
        return sizeEstimate;
    }

    @Override
    public boolean hasPropertyValue(OWLObject p, OWLObject s, OWLObject o) {
        final OWLPropertyExpression pex = asOWLPropertyExpression(p);

        if (pex.isObjectPropertyExpression()) {
            return r.isEntailed(f.getOWLObjectPropertyAssertionAxiom(
                    (OWLObjectPropertyExpression) pex, asOWLNamedIndividual(s),
                    asOWLNamedIndividual(o)));
        } else if (pex.isDataPropertyExpression()) {
            return r.isEntailed(f.getOWLDataPropertyAssertionAxiom(
                    (OWLDataPropertyExpression) pex, asOWLNamedIndividual(s),
                    asOWLLiteral(o)));

        }
        return false;
    }

    @Override
    public boolean isClassAlwaysNonEmpty(OWLObject sc) {
        final OWLAxiom axiom = f.getOWLSubClassOfAxiom(
                asOWLClassExpression(sc), f.getOWLNothing());

        try {
            m.applyChange(new AddAxiom(o, axiom));

            boolean classAlwaysNonEmpty = !r.isConsistent();

            m.applyChange(new RemoveAxiom(o, axiom));

            return classAlwaysNonEmpty;
        } catch (OWLOntologyChangeException e) {
            throw new InternalReasonerException();
        }
    }

    @Override
    public boolean isClassified() {
        // TODO
        // return r.isClassified();
        return false;
    }

    @Override
    public boolean isSatisfiable(OWLObject arg) {
        return r.isSatisfiable(asOWLClassExpression(arg));
    }

    @Override
    public Set<? extends OWLObject> retrieveIndividualsWithProperty(
            OWLObject odpe) {
        final OWLPropertyExpression ope = asOWLPropertyExpression(odpe);

        final Set<OWLObject> set = new HashSet<OWLObject>();
        try {
            if (ope.isObjectPropertyExpression()) {
                for (final OWLNamedIndividual i : getIndividuals()) {
                    if (!r.getObjectPropertyValues(i,
                            (OWLObjectPropertyExpression) ope).isEmpty()) {
                        set.add(i);
                    }
                }
            } else if (ope.isObjectPropertyExpression()) {
                for (final OWLNamedIndividual i : getIndividuals()) {
                    if (!r.getObjectPropertyValues(i,
                            (OWLObjectPropertyExpression) ope).isEmpty()) {
                        set.add(i);
                    }
                }
            }
        } catch (Exception e) {
            throw new InternalReasonerException(e);
        }
        return set;
    }

    @Override
    public Map<OWLObject, Boolean> getKnownInstances(final OWLObject ce) {
        final Map<OWLObject, Boolean> m = new HashMap<OWLObject, Boolean>();
        final OWLClassExpression cex = asOWLClassExpression(ce);

        for (final OWLObject x : getIndividuals()) {
            m.put(x, false);
        }

        if (!cex.isAnonymous()) {
            for (final OWLObject x : EntitySearcher.getIndividuals(
                    cex.asOWLClass(), o)) {
                m.put(x, true);
            }
        }

        return m;
    }

    @Override
    public Boolean isKnownTypeOf(OWLObject ce, OWLObject i) {
        final OWLIndividual ii = asOWLNamedIndividual(i);

        if (EntitySearcher.getTypes(ii, o).contains(ce)) {
            return true;
        }

        return null;
    }

    @Override
    public Boolean hasKnownPropertyValue(OWLObject p, OWLObject s, OWLObject ob) {
        final OWLIndividual is = asOWLNamedIndividual(s);
        final OWLPropertyExpression pex = asOWLPropertyExpression(p);

        if (pex != null) {
            if (pex.isObjectPropertyExpression()) {
                final OWLObjectPropertyExpression ope = ((OWLObjectPropertyExpression) p)
                        .getSimplified();

                if (ope instanceof OWLObjectInverseOf) {
                    final OWLObjectPropertyExpression opeInv = ope
                            .getInverseProperty().getSimplified();

                    for (final OWLObjectPropertyAssertionAxiom ax : o
                            .getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
                        if (ax.getObject().equals(s)
                                && ax.getProperty().equals(opeInv)
                                && ax.getSubject().equals(ob)) {
                            return true;
                        }
                    }
                    return false;
                } else {
                    return EntitySearcher.getObjectPropertyValues(is,
                            (OWLObjectPropertyExpression) pex, o).contains(ob);
                }
            } else if (pex.isDataPropertyExpression()) {
                return EntitySearcher.getDataPropertyValues(is,
                        (OWLDataPropertyExpression) pex, o).contains(ob);
            }
        }

        return false;
    }

    @Override
    public Set<? extends OWLObject> getInstances(OWLObject ic, boolean direct) {
        final OWLClassExpression c = asOWLClassExpression(ic);

        return r.getInstances(c, direct).getFlattened();
    }

    @Override
    public OWL2QueryFactory<OWLObject> getFactory() {
        return factory;
    }

    @Override
    public boolean isRealized() {
        // try {
        // return r.isRealised();
        // } catch (OWLReasonerException e) {
        // throw new InternalReasonerException();
        // }
        // TODO
        return false;
    }

    @Override
    public boolean isComplexClass(OWLObject c) {
        return c instanceof OWLClassExpression;
    }

    @Override
    public Collection<? extends OWLObject> getKnownPropertyValues(
            OWLObject pvP, OWLObject pvI) {

        final OWLPropertyExpression p = asOWLPropertyExpression(pvP);
        final OWLNamedIndividual ni = asOWLNamedIndividual(pvI);

        Collection<? extends OWLObject> result = Collections.emptySet();

        if (p == null || ni == null) {
            return result;
        }

        if (p.isObjectPropertyExpression()) {
            result = structuralReasoner.getObjectPropertyValues(ni,
                    (OWLObjectPropertyExpression) p).getFlattened();
        } else if (p.isDataPropertyExpression() && !p.isAnonymous()) {
            result = structuralReasoner.getDataPropertyValues(ni,
                    (OWLDataProperty) p);
        } else {
            throw new IllegalArgumentException();
        }

        return result;
    }

    private final Hierarchy<OWLObject, OWLClass> classHierarchy = new Hierarchy<OWLObject, OWLClass>() {

        @Override
        public Set<OWLClass> getEquivs(OWLObject ce) {
            final OWLClassExpression cex = asOWLClassExpression(ce);
            Set<OWLClass> set = r.getEquivalentClasses(cex).getEntities();
            return set;
        }

        @Override
        public Set<OWLClass> getSubs(OWLObject superCE, boolean direct) {
            final OWLClassExpression cex = asOWLClassExpression(superCE);
            Set<OWLClass> set = r.getSubClasses(cex, direct).getFlattened();
            if (!direct) {
                set.add(f.getOWLNothing());
            }
            return set;
        }

        @Override
        public Set<OWLClass> getSupers(OWLObject superCE, boolean direct) {
            final OWLClassExpression cex = asOWLClassExpression(superCE);
            Set<OWLClass> set = r.getSuperClasses(cex, direct).getFlattened();
            if (!direct) {
                set.add(f.getOWLThing());
            }
            return set;
        }

        @Override
        public boolean isEquiv(OWLObject equivG1, OWLObject equivG2) {
            return r.isEntailed(f.getOWLEquivalentClassesAxiom(
                    asOWLClassExpression(equivG1),
                    asOWLClassExpression(equivG2)));
        }

        @Override
        public boolean isSub(OWLObject subG1, OWLObject superG2, boolean direct) {
            return r.isEntailed(f.getOWLSubClassOfAxiom(
                    asOWLClassExpression(subG1), asOWLClassExpression(superG2)));
            // return getSubs(superG2,direct).contains(subG1);
        }

        @Override
        public boolean isDisjointWith(OWLObject disjointG1, OWLObject disjointG2) {
            return r.isEntailed(f.getOWLDisjointClassesAxiom(
                    asOWLClassExpression(disjointG1),
                    asOWLClassExpression(disjointG2)));
        }

        @Override
        public Set<OWLClass> getComplements(OWLObject complementG) {
            return r.getEquivalentClasses(
                    f.getOWLObjectComplementOf(asOWLClassExpression(complementG)))
                    .getEntities();
        }

        @Override
        public boolean isComplementWith(OWLObject complementG1,
                OWLObject complementG2) {
            return r.isEntailed(f.getOWLEquivalentClassesAxiom(
                    f.getOWLObjectComplementOf(asOWLClassExpression(complementG1)),
                    asOWLClassExpression(complementG2)));
        }

        @Override
        public Set<OWLClass> getTops() {
            return Collections.singleton(f.getOWLThing());
        }

        @Override
        public Set<OWLClass> getBottoms() {
            return Collections.singleton(f.getOWLNothing());
        }

        @Override
        public Set<OWLClass> getDisjoints(OWLObject disjointG) {
            return r.getDisjointClasses(asOWLClassExpression(disjointG))
                    .getFlattened();
        }

    };

    @Override
    public Hierarchy<OWLObject, OWLClass> getClassHierarchy() {
        return classHierarchy;
    }

    private final Hierarchy<OWLObject, OWLClass> toldClassHierarchy = new Hierarchy<OWLObject, OWLClass>() {

        @Override
        public Set<OWLClass> getEquivs(OWLObject ce) {
            final OWLClassExpression cex = asOWLClassExpression(ce);
            if (cex.isAnonymous()) {
                return Collections.emptySet();
            } else {
                return structuralReasoner.getEquivalentClasses(cex)
                        .getEntities();
                // final Set<OWLClass> set = new HashSet<OWLClass>();
                // for (final OWLClassExpression oce : cex.asOWLClass()
                // .getEquivalentClasses(o)) {
                // if (!oce.isAnonymous()) {
                // set.add(oce.asOWLClass());
                // }
                // }
                // return set;
            }
        }

        @Override
        public Set<OWLClass> getSubs(OWLObject superCE, boolean direct) {
            final OWLClassExpression cex = asOWLClassExpression(superCE);
            if (cex.isAnonymous()) {
                return Collections.emptySet();
            } else {
                // final Set<OWLClass> set = new HashSet<OWLClass>();
                return structuralReasoner.getSubClasses(cex, direct)
                        .getFlattened();
                //
                // for (final OWLClassExpression oce : cex.asOWLClass()
                // .getSubClasses(o)) {
                // if (!oce.isAnonymous() && !set.contains(oce.asOWLClass())) {
                // set.add(oce.asOWLClass());
                // if (!direct) {
                // set.addAll(getSubs(oce, direct));
                // }
                // }
                // }
                //
                // return set;
            }
        }

        @Override
        public Set<OWLClass> getSupers(OWLObject superCE, boolean direct) {
            final OWLClassExpression cex = asOWLClassExpression(superCE);
            if (cex.isAnonymous()) {
                return Collections.emptySet();
            } else {
                return structuralReasoner.getSuperClasses(cex, direct)
                        .getFlattened();
                //
                // final Set<OWLClass> set = new HashSet<OWLClass>();
                // for (final OWLClassExpression oce : cex.asOWLClass()
                // .getSuperClasses(o)) {
                // if (!oce.isAnonymous() && !set.contains(oce.asOWLClass())) {
                // set.add(oce.asOWLClass());
                // if (!direct) {
                // set.addAll(getSupers(oce, direct));
                // }
                // }
                // }
                //
                // return set;
            }
        }

        @Override
        public Set<OWLClass> getTops() {
            return Collections.singleton(f.getOWLThing());
        }

        @Override
        public Set<OWLClass> getBottoms() {
            return Collections.singleton(f.getOWLNothing());
        }

        @Override
        public Set<OWLClass> getDisjoints(OWLObject disjointG) {
            final OWLClassExpression cex = asOWLClassExpression(disjointG);
            if (cex.isAnonymous()) {
                return Collections.emptySet();
            } else {
                return structuralReasoner.getDisjointClasses(cex)
                        .getFlattened();
            }
        }

        @Override
        public boolean isEquiv(OWLObject equivG1, OWLObject equivG2) {
            return structuralReasoner.isEntailed(f
                    .getOWLEquivalentClassesAxiom(
                            asOWLClassExpression(equivG1),
                            asOWLClassExpression(equivG2)));
        }

        @Override
        public boolean isSub(OWLObject subG1, OWLObject superG2, boolean direct) {
            return getSubs(superG2, direct).contains(subG1);
        }

        @Override
        public boolean isDisjointWith(OWLObject disjointG1, OWLObject disjointG2) {
            return structuralReasoner.isEntailed(f.getOWLDisjointClassesAxiom(
                    asOWLClassExpression(disjointG1),
                    asOWLClassExpression(disjointG2)));
        }

        @Override
        public Set<OWLClass> getComplements(OWLObject complementG) {
            return structuralReasoner
                    .getEquivalentClasses(
                            f.getOWLObjectComplementOf(asOWLClassExpression(complementG)))
                    .getEntities();
        }

        @Override
        public boolean isComplementWith(OWLObject complementG1,
                OWLObject complementG2) {
            return structuralReasoner
                    .isEntailed(f.getOWLEquivalentClassesAxiom(
                                    f.getOWLObjectComplementOf(asOWLClassExpression(complementG1)),
                                    asOWLClassExpression(complementG2)));
        }

    };

    @Override
    public Hierarchy<OWLObject, OWLClass> getToldClassHierarchy() {
        return toldClassHierarchy;
    }

    private final Hierarchy<OWLObject, OWLProperty> propertyHierarchy = new Hierarchy<OWLObject, OWLProperty>() {

        @Override
        public Set<OWLProperty> getEquivs(OWLObject ce) {
            final OWLPropertyExpression cex = asOWLPropertyExpression(ce);

            if (cex.isDataPropertyExpression()) {
                return new HashSet<OWLProperty>(r.getEquivalentDataProperties(
                        (OWLDataProperty) cex).getEntities());
            } else if (cex.isObjectPropertyExpression()) {
                final Set<OWLProperty> props = new HashSet<OWLProperty>();
                for (final OWLObjectPropertyExpression ex : r
                        .getEquivalentObjectProperties((OWLObjectProperty) cex)
                        .getEntities()) {
                    if (ex.isAnonymous()) {
                        continue;
                    } else {
                        props.add(ex.asOWLObjectProperty());
                    }
                }
                return props;
            } else {
                throw new InternalReasonerException();
            }
        }

        @Override
        public Set<OWLProperty> getSubs(OWLObject superCE, boolean direct) {
            final OWLPropertyExpression cex = asOWLPropertyExpression(superCE);
            final Set<OWLProperty> set = new HashSet<OWLProperty>();

            if (cex.isDataPropertyExpression()) {
                set.addAll(r
                        .getSubDataProperties((OWLDataProperty) cex, direct)
                        .getFlattened());
                if (!direct) {
                    set.add(f.getOWLBottomDataProperty());
                }
            } else if (cex.isObjectPropertyExpression()) {
                final Set<OWLProperty> props = new HashSet<OWLProperty>();
                for (final OWLObjectPropertyExpression ex : r
                        .getSubObjectProperties((OWLObjectProperty) cex, direct)
                        .getFlattened()) {
                    if (ex.isAnonymous()) {
                        continue;
                    } else {
                        set.add(ex.asOWLObjectProperty());
                    }
                }

                if (!direct) {
                    set.add(f.getOWLBottomObjectProperty());
                }
            } else {
                throw new InternalReasonerException();
            }

            return set;
        }

        @Override
        public Set<OWLProperty> getSupers(OWLObject superCE, boolean direct) {
            final OWLPropertyExpression cex = asOWLPropertyExpression(superCE);
            final Set<OWLProperty> set = new HashSet<OWLProperty>();

            if (cex.isDataPropertyExpression()) {
                set.addAll(r.getSuperDataProperties((OWLDataProperty) cex,
                        direct).getFlattened());

                if (!direct) {
                    set.add(f.getOWLTopDataProperty());
                }
            } else if (cex.isObjectPropertyExpression()) {
                for (final OWLObjectPropertyExpression ex : r
                        .getSuperObjectProperties(
                                (OWLObjectPropertyExpression) cex, direct)
                        .getFlattened()) {
                    if (ex.isAnonymous()) {
                        continue;
                    } else {
                        set.add(ex.asOWLObjectProperty());
                    }
                }
                if (!direct) {
                    set.add(f.getOWLTopObjectProperty());
                }
            } else {
                throw new InternalReasonerException();
            }

            return set;
        }

        @Override
        public Set<OWLProperty> getTops() {
            return new HashSet<OWLProperty>(Arrays.asList(
                    f.getOWLTopObjectProperty(), f.getOWLTopDataProperty()));
        }

        @Override
        public Set<OWLProperty> getBottoms() {
            return new HashSet<OWLProperty>(Arrays.asList(
                    f.getOWLBottomObjectProperty(),
                    f.getOWLBottomDataProperty()));
        }

        @Override
        public Set<OWLProperty> getDisjoints(OWLObject disjointG) {
            final OWLPropertyExpression cex = asOWLPropertyExpression(disjointG);

            if (cex.isDataPropertyExpression()) {
                return new HashSet<OWLProperty>(r.getDisjointDataProperties(
                        (OWLDataProperty) cex).getFlattened());
            } else if (cex.isObjectPropertyExpression()) {
                final Set<OWLProperty> props = new HashSet<OWLProperty>();
                for (final OWLObjectPropertyExpression ex : r
                        .getDisjointObjectProperties((OWLObjectProperty) cex)
                        .getFlattened()) {
                    if (ex.isAnonymous()) {
                        continue;
                    } else {
                        props.add(ex.asOWLObjectProperty());
                    }
                }
                return props;
            } else {
                throw new InternalReasonerException();
            }
        }

        @Override
        public boolean isEquiv(OWLObject equivG1, OWLObject equivG2) {
            final OWLPropertyExpression cex1 = asOWLPropertyExpression(equivG1);
            final OWLPropertyExpression cex2 = asOWLPropertyExpression(equivG2);

            if (cex1.isDataPropertyExpression()) {
                return cex2.isDataPropertyExpression()
                        && r.isEntailed(f.getOWLEquivalentDataPropertiesAxiom(
                                        (OWLDataPropertyExpression) cex1,
                                        (OWLDataPropertyExpression) cex2));
            } else {
                return cex2.isObjectPropertyExpression()
                        && r.isEntailed(f
                                .getOWLEquivalentObjectPropertiesAxiom(
                                        (OWLObjectPropertyExpression) cex1,
                                        (OWLObjectPropertyExpression) cex2));
            }
        }

        @Override
        public boolean isSub(OWLObject subG1, OWLObject superG2, boolean direct) {
            return getSubs(superG2, direct).contains(subG1);
        }

        @Override
        public boolean isDisjointWith(OWLObject disjointG1, OWLObject disjointG2) {
            return getDisjoints(disjointG1).contains(disjointG2); // TODO
            // reasoner
            // directly
        }

        @Override
        public Set<OWLProperty> getComplements(OWLObject complementG) {
            throw new UnsupportedOperationException("NOT supported yet.");
        }

        @Override
        public boolean isComplementWith(OWLObject complementG1,
                OWLObject complementG2) {
            throw new UnsupportedOperationException("NOT supported yet.");
        }

    };

    @Override
    public Hierarchy<OWLObject, OWLProperty> getPropertyHierarchy() {
        return propertyHierarchy;
    }

    @Override
    public Set<OWLProperty> getFunctionalProperties() {
        final Set<OWLProperty> set = new HashSet<OWLProperty>();

        for (final OWLObjectProperty p : getObjectProperties()) {
            if (r.isEntailed(f.getOWLFunctionalObjectPropertyAxiom(p
                    .asOWLObjectProperty()))) {
                set.add(p);
            }
        }

        for (final OWLDataProperty p : getDataProperties()) {
            if (r.isEntailed(f.getOWLFunctionalDataPropertyAxiom(p
                    .asOWLDataProperty()))) {
                set.add(p);
            }
        }

        return set;
    }

    @Override
    public Set<? extends OWLObject> getInverseFunctionalProperties() {
        final Set<OWLObjectProperty> set = new HashSet<OWLObjectProperty>();

        for (final OWLObjectProperty p : getObjectProperties()) {
            if (r.isEntailed(f.getOWLInverseFunctionalObjectPropertyAxiom(p))) {
                set.add(p);
            }
        }

        return set;
    }

    @Override
    public Set<? extends OWLObject> getIrreflexiveProperties() {
        final Set<OWLObjectProperty> set = new HashSet<OWLObjectProperty>();

        for (final OWLObjectProperty p : getObjectProperties()) {
            if (r.isEntailed(f.getOWLIrreflexiveObjectPropertyAxiom(p))) {
                set.add(p);
            }
        }

        return set;
    }

    @Override
    public Set<? extends OWLObject> getReflexiveProperties() {
        final Set<OWLObjectProperty> set = new HashSet<OWLObjectProperty>();

        for (final OWLObjectProperty p : getObjectProperties()) {
            if (r.isEntailed(f.getOWLReflexiveObjectPropertyAxiom(p))) {
                set.add(p);
            }
        }

        return set;
    }

    @Override
    public Set<? extends OWLObject> getSymmetricProperties() {
        final Set<OWLObjectProperty> set = new HashSet<OWLObjectProperty>();

        for (final OWLObjectProperty p : getObjectProperties()) {
            if (r.isEntailed(f.getOWLSymmetricObjectPropertyAxiom(p))) {
                set.add(p);
            }
        }

        return set;
    }

    @Override
    public Set<OWLObjectProperty> getAsymmetricProperties() {
        final Set<OWLObjectProperty> set = new HashSet<OWLObjectProperty>();

        for (final OWLObjectProperty p : getObjectProperties()) {
            if (r.isEntailed(f.getOWLAsymmetricObjectPropertyAxiom(p))) {
                set.add(p);
            }
        }

        return set;
    }

    @Override
    public Set<? extends OWLObject> getTransitiveProperties() {
        final Set<OWLObjectProperty> set = new HashSet<OWLObjectProperty>();

        for (final OWLObjectProperty p : getObjectProperties()) {
            if (r.isEntailed(f.getOWLTransitiveObjectPropertyAxiom(p))) {
                set.add(p);
            }
        }

        return set;
    }

    @Override
    public boolean isFunctionalProperty(OWLObject Term) {
        final OWLPropertyExpression p = asOWLPropertyExpression(Term);

        if (p instanceof OWLObjectProperty) {
            return r.isEntailed(f
                    .getOWLFunctionalObjectPropertyAxiom(asOWLObjectProperty(Term)));
        } else if (p instanceof OWLDataProperty) {
            return r.isEntailed(f
                    .getOWLFunctionalDataPropertyAxiom((OWLDataProperty) Term));
        } else {
            return false;
        }
    }

    @Override
    public boolean isInverseFunctionalProperty(OWLObject Term) {
        return r.isEntailed(f
                .getOWLInverseFunctionalObjectPropertyAxiom(asOWLObjectProperty(Term)));
    }

    @Override
    public boolean isIrreflexiveProperty(OWLObject Term) {
        return r.isEntailed(f
                .getOWLIrreflexiveObjectPropertyAxiom(asOWLObjectProperty(Term)));
    }

    @Override
    public boolean isReflexiveProperty(OWLObject Term) {
        return r.isEntailed(f
                .getOWLReflexiveObjectPropertyAxiom(asOWLObjectProperty(Term)));
    }

    @Override
    public boolean isSymmetricProperty(OWLObject Term) {
        return r.isEntailed(f
                .getOWLSymmetricObjectPropertyAxiom(asOWLObjectProperty(Term)));

    }

    @Override
    public boolean isAsymmetricProperty(OWLObject Term) {
        return r.isEntailed(f
                .getOWLAsymmetricObjectPropertyAxiom(asOWLObjectProperty(Term)));
    }

    @Override
    public boolean isTransitiveProperty(OWLObject Term) {
        return r.isEntailed(f
                .getOWLTransitiveObjectPropertyAxiom(asOWLObjectProperty(Term)));
    }

    @Override
    public String getDatatypeOfLiteral(OWLObject literal) {
        if (literal instanceof OWLLiteral) {
            return ((OWLLiteral) literal).getDatatype().getIRI().toString();
        } else {
            throw new OWL2QueryException("Expected literal, but got " + literal);
        }
    }
}
