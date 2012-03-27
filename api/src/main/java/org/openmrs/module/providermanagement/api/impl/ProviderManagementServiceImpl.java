/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.providermanagement.api.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.api.APIException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.providermanagement.ProviderManagementConstants;
import org.openmrs.module.providermanagement.ProviderManagementUtils;
import org.openmrs.module.providermanagement.ProviderRole;
import org.openmrs.module.providermanagement.api.ProviderManagementService;
import org.openmrs.module.providermanagement.api.db.ProviderManagementDAO;
import org.openmrs.module.providermanagement.exception.*;

import java.util.*;

/**
 * It is a default implementation of {@link ProviderManagementService}.
 */
public class ProviderManagementServiceImpl extends BaseOpenmrsService implements ProviderManagementService {

    // TODO: create a retire handler that ended provider/patient relationships and supervisor/supervisee relationships when a provider is retired?
    // TODO: what about a "purge" handler when a provider is purged?

    // TODO: change exception for person is not a provider to use the new exception class?

	protected final Log log = LogFactory.getLog(this.getClass());
	
	private ProviderManagementDAO dao;
    
    private static ProviderAttributeType providerRoleAttributeType = null;
	
	/**
     * @param dao the dao to set
     */
    public void setDao(ProviderManagementDAO dao) {
	    this.dao = dao;
    }

    /**
     * @return the dao
     */
    public ProviderManagementDAO getDao() {
	    return dao;
    }

    @Override
    public ProviderAttributeType getProviderRoleAttributeType() {
        // TODO: error handling?

        if (providerRoleAttributeType == null) {
            providerRoleAttributeType = Context.getProviderService().getProviderAttributeTypeByUuid(ProviderManagementConstants.PROVIDER_ROLE_ATTRIBUTE_TYPE_UUID);
        }

        return providerRoleAttributeType;
    }

    @Override
    public List<ProviderRole> getAllProviderRoles() {
        return dao.getAllProviderRoles(false);
    }

    @Override
    public List<ProviderRole> getAllProviderRoles(boolean includeRetired) {
        return dao.getAllProviderRoles(includeRetired);
    }

    @Override
    public ProviderRole getProviderRole(Integer id) {
        return dao.getProviderRole(id);
    }

    @Override
    public ProviderRole getProviderRoleByUuid(String uuid) {
        return dao.getProviderRoleByUuid(uuid);
    }

    @Override
    public List<ProviderRole> getProviderRolesByRelationshipType(RelationshipType relationshipType) {
        if (relationshipType == null) {
            throw new APIException("relationshipType cannot be null");
        }
        else {
            return dao.getProviderRolesByRelationshipType(relationshipType);
        }
    }

    @Override
    public List<ProviderRole> getProviderRolesBySuperviseeProviderRole(ProviderRole providerRole) {
        if (providerRole == null) {
            throw new APIException("providerRole cannot be null");
        }
        else {
            return dao.getProviderRolesBySuperviseeProviderRole(providerRole);
        }
    }

    @Override
    public void saveProviderRole(ProviderRole role) {
        dao.saveProviderRole(role);
    }

    @Override
    public void retireProviderRole(ProviderRole role, String reason) {
        // BaseRetireHandler handles retiring the object
        dao.saveProviderRole(role);
    }

    @Override
    public void unretireProviderRole(ProviderRole role) {
        // BaseUnretireHandler handles unretiring the object
        dao.saveProviderRole(role);
    }

    @Override
    public void purgeProviderRole(ProviderRole role) {
        dao.deleteProviderRole(role);
    }

    @Override
    public List<RelationshipType> getAllProviderRoleRelationshipTypes(boolean includeRetired) {

        Set<RelationshipType> relationshipTypes = new HashSet<RelationshipType>();

        for (ProviderRole providerRole : getAllProviderRoles(includeRetired)) {
            
            if (includeRetired == true) {
                relationshipTypes.addAll(providerRole.getRelationshipTypes());
            }
            // filter out any retired relationships
            else {
                relationshipTypes.addAll(CollectionUtils.select(providerRole.getRelationshipTypes(), new Predicate() {
                    @Override
                    public boolean evaluate(Object relationshipType) {
                        return ((RelationshipType) relationshipType).getRetired() == true ? false : true;
                    }
                }));
            }
        }

        return new ArrayList<RelationshipType>(relationshipTypes);
    }

    @Override
    public List<RelationshipType> getAllProviderRoleRelationshipTypes() {
        return getAllProviderRoleRelationshipTypes(false);
    }

    @Override
    public void assignProviderRoleToProvider(Person provider, ProviderRole role, String identifier) {
        // TODO: make sure this syncs properly!

        if (provider == null) {
            throw new APIException("Cannot set provider role: provider is null");
        }
        
        if (role == null) {
            throw new APIException("Cannot set provider role: role is null");
        }
        
        if (identifier == null) {
            throw new APIException("Cannot set provider role: identifier is null");
        }

        if (provider.isVoided()) {
            throw new APIException("Cannot set provider role: underlying person has been voided");
        }

        if (ProviderManagementUtils.hasRole(provider,role)) {
            // if the provider already has this role, do nothing
            return;
        }
        
        // create a new provider object and associate it with this person
        Provider p = new Provider();
        p.setPerson(provider);
        p.setIdentifier(identifier);
        
        // create a new provider role attribute 
        ProviderAttribute providerRoleAttribute = new ProviderAttribute();
        providerRoleAttribute.setAttributeType(getProviderRoleAttributeType());
        providerRoleAttribute.setValue(role);
        p.setAttribute(providerRoleAttribute);
        
        // save this new provider
        Context.getProviderService().saveProvider(p);
    }

    @Override
    public void unassignProviderRoleFromProvider(Person provider, ProviderRole role) {
        // TODO: make sure this syncs properly!

        if (provider == null) {
            throw new APIException("Cannot set provider role: provider is null");
        }

        if (role == null) {
            throw new APIException("Cannot set provider role: role is null");
        }

        if (!ProviderManagementUtils.hasRole(provider,role)) {
            // if the provider doesn't have this role, do nothing
            return;
        }

        // note that we don't check to make sure this provider is a person

        // iterate through all the providers and retire any with the specified role
        for (Provider p : Context.getProviderService().getProvidersByPerson(provider)) {
            if (ProviderManagementUtils.getProviderRole(p).equals(role)) {
                Context.getProviderService().retireProvider(p, "removing provider role " + role + " from " + provider);
            }
        }
    }

    @Override
    public List<Person> getProvidersByRoles(List<ProviderRole> roles) {

        // TODO: this won't distinguish between retired and unretired providers until TRUNK-3170 is implemented

        // not allowed to pass null or empty set here
        if (roles == null || roles.isEmpty()) {
            throw new APIException("Roles cannot be null or empty");
        }

        // TODO: figure out if we want to sort results here

        List<Provider> providers = new ArrayList<Provider>();

        // iterate through each role and fetch the matching providers for each role
        // note that since a provider can only have one role, we don't
        // have to worry about duplicates, ie. fetching the same provider twice

        // TODO: but duplicate Persons could be a possibility...?

        for (ProviderRole role : roles) {
            // create the attribute type to add to the query
            Map<ProviderAttributeType, Object> attributeValueMap = new HashMap<ProviderAttributeType, Object>();
            attributeValueMap.put(getProviderRoleAttributeType(), role);
            // find all providers with that role
            providers.addAll(Context.getProviderService().getProviders(null, null, null, attributeValueMap));
        }

        return providersToPersons(providers);
    }

    @Override
    public List<Person> getProvidersByRole(ProviderRole role) {

        // TODO: this won't distinguish between retired and unretired providers until TRUNK-3170 is implemented

        // not allowed to pass null here
        if (role == null) {
            throw new APIException("Role cannot be null");
        }

        List<ProviderRole> roles = new ArrayList<ProviderRole>();
        roles.add(role);
        return getProvidersByRoles(roles);
    }

    @Override
    public List<Person> getProvidersByRelationshipType(RelationshipType relationshipType) {

        if (relationshipType == null) {
            throw new  APIException("Relationship type cannot be null");
        }

        // first fetch the roles that support this relationship type, then fetch all the providers with those roles
        List<ProviderRole> providerRoles = getProviderRolesByRelationshipType(relationshipType);
        if (providerRoles == null || providerRoles.size() == 0) {
            return new ArrayList<Person>();  // just return an empty list
        }
        else {
            return getProvidersByRoles(providerRoles);
        }
    }

    @Override
    public List<Person> getProvidersBySuperviseeProviderRole(ProviderRole role) {
       
        if (role == null) {
            throw new APIException("Provider role cannot be null");
        }
        
        // first fetch the roles that can supervise this relationship type, then fetch all providers with those roles
        List<ProviderRole> providerRoles = getProviderRolesBySuperviseeProviderRole(role);
        if (providerRoles == null || providerRoles.size() == 0) {
            return new ArrayList<Person>();  // just return an empty list
        }
        else {
            return getProvidersByRoles(providerRoles);
        }
    }

    @Override
    public void assignPatientToProvider(Patient patient, Person provider, RelationshipType relationshipType, Date date)
            throws ProviderDoesNotSupportRelationshipTypeException, PatientAlreadyAssignedToProviderException,
            PersonIsNotProviderException {

        if (patient == null) {
            throw new APIException("Patient cannot be null");
        }

        if (provider == null) {
            throw new APIException("Provider cannot be null");
        }

        if (relationshipType == null) {
            throw new APIException("Relationship type cannot be null");
        }

        if (patient.isVoided()) {
            throw new APIException("Patient cannot be voided");
        }

        if (!ProviderManagementUtils.isProvider(provider)) {
             throw new PersonIsNotProviderException(provider + " is not a provider");
        }
        
        if (!ProviderManagementUtils.supportsRelationshipType(provider, relationshipType)) {
            throw new ProviderDoesNotSupportRelationshipTypeException(provider + " cannot support " + relationshipType);
        }

        // use current date if no date specified
        if (date == null) {
            date = new Date();
        }

        // TODO: what about voided relationships?  does the get relationships method ignore voided?

        // test to mark sure the relationship doesn't already exist
        List<Relationship> relationships = Context.getPersonService().getRelationships(provider, patient, relationshipType, date);
        if (relationships != null && relationships.size() > 0) {
            throw new PatientAlreadyAssignedToProviderException("Provider " + provider + " is already assigned to " + patient + " with a " + relationshipType + "relationship");
        }
        
        // go ahead and create the relationship
        Relationship relationship = new Relationship();
        relationship.setPersonA(provider);
        relationship.setPersonB(patient);
        relationship.setRelationshipType(relationshipType);
        relationship.setStartDate(ProviderManagementUtils.clearTimeComponent(date));
        Context.getPersonService().saveRelationship(relationship);
    }

    @Override
    public void assignPatientToProvider(Patient patient, Person provider, RelationshipType relationshipType)
            throws ProviderDoesNotSupportRelationshipTypeException, PatientAlreadyAssignedToProviderException,
            PersonIsNotProviderException {
        assignPatientToProvider(patient, provider, relationshipType, new Date());
    }

    @Override
    public void unassignPatientFromProvider(Patient patient, Person provider, RelationshipType relationshipType, Date date)
        throws PatientNotAssignedToProviderException, PersonIsNotProviderException, InvalidRelationshipTypeException {

        if (patient == null) {
            throw new APIException("Patient cannot be null");
        }

        if (provider == null) {
            throw new APIException("Provider cannot be null");
        }

        if (relationshipType == null) {
            throw new APIException("Relationship type cannot be null");
        }

        if (patient.isVoided()) {
            throw new APIException("Patient cannot be voided");
        }

        if (!ProviderManagementUtils.isProvider(provider)) {
            throw new PersonIsNotProviderException(provider + " is not a provider");
        }

        // we don't need to assure that the person supports the relationship type, but we need to make sure this a provider/patient relationship type
        if (!getAllProviderRoleRelationshipTypes().contains(relationshipType)) {
            throw new InvalidRelationshipTypeException("Invalid relationship type: " + relationshipType + " is not a provider/patient relationship type");
        }

        // use current date if no date specified
        if (date == null) {
            date = new Date();
        }

        // find the existing relationship
        List<Relationship> relationships = Context.getPersonService().getRelationships(provider, patient, relationshipType, date);
        if (relationships == null || relationships.size() == 0) {
            throw new PatientNotAssignedToProviderException("Provider " + provider + " is not assigned to " + patient + " with a " + relationshipType + " relationship");
        }
        if (relationships.size() > 1) {
            // TODO: handle this better? maybe void all but one automatically?
            throw new APIException("Duplicate " + relationshipType + " between " + provider + " and " + patient);
        }

        // go ahead and set the end date of the relationship
        Relationship relationship = relationships.get(0);
        relationship.setEndDate(ProviderManagementUtils.clearTimeComponent(date));
        Context.getPersonService().saveRelationship(relationship);
    }

    @Override
    public void unassignPatientFromProvider(Patient patient, Person provider, RelationshipType relationshipType)
            throws PatientNotAssignedToProviderException, PersonIsNotProviderException, InvalidRelationshipTypeException {
        unassignPatientFromProvider(patient, provider, relationshipType, new Date());
    }

    @Override
    public void unassignAllPatientsFromProvider(Person provider, RelationshipType relationshipType)
            throws PersonIsNotProviderException, InvalidRelationshipTypeException {

        if (provider == null) {
            throw new APIException("Provider cannot be null");
        }

        if (relationshipType == null) {
            throw new APIException("Relationship type cannot be null");
        }

        if (!ProviderManagementUtils.isProvider(provider)) {
            throw new PersonIsNotProviderException(provider + " is not a provider");
        }

        // we don't need to assure that the person supports the relationship type, but we need to make sure this a provider/patient relationship type
        if (!getAllProviderRoleRelationshipTypes().contains(relationshipType)) {
            throw new InvalidRelationshipTypeException("Invalid relationship type: " + relationshipType + " is not a provider/patient relationship type");
        }

        // go ahead and end each relationship on the current date
        List<Relationship> relationships =
                Context.getPersonService().getRelationships(provider, null, relationshipType, ProviderManagementUtils.clearTimeComponent(new Date()));
        if (relationships != null || relationships.size() > 0) {
            for (Relationship relationship : relationships) {
                relationship.setEndDate(ProviderManagementUtils.clearTimeComponent(new Date()));
                Context.getPersonService().saveRelationship(relationship);
            }
        }
    }

    @Override
    public void unassignAllPatientsFromProvider(Person provider)
            throws PersonIsNotProviderException {
        
        if (provider == null) {
            throw new APIException("Provider cannot be null");
        }

        for (RelationshipType relationshipType : getAllProviderRoleRelationshipTypes()) {
            try {
                unassignAllPatientsFromProvider(provider, relationshipType);
            }
            catch (InvalidRelationshipTypeException e) {
                // we should never get this exception, since getAlProviderRoleRelationshipTypes
                // should only return valid relationship types; so if we do get this exception, throw a runtime exception
                // instead of forcing calling methods to catch it
                throw new APIException(e);
            }
        }
    }

    @Override
    public List<Patient> getPatients(Person provider, RelationshipType relationshipType, Date date)
            throws PersonIsNotProviderException, InvalidRelationshipTypeException {

        if (provider == null) {
            throw new APIException("Provider cannot be null");
        }

        if (relationshipType == null) {
            throw new APIException("Relationship type cannot be null");
        }

        if (!ProviderManagementUtils.isProvider(provider)) {
            throw new PersonIsNotProviderException(provider + " is not a provider");
        }

        if (!getAllProviderRoleRelationshipTypes().contains(relationshipType)) {
            throw new InvalidRelationshipTypeException("Invalid relationship type: " + relationshipType + " is not a provider/patient relationship type");
        }

        // use current date if no date specified
        if (date == null) {
            date = new Date();
        }

        // get the specified relationships for the provider
        List<Relationship> relationships =
                Context.getPersonService().getRelationships(provider, null, relationshipType, ProviderManagementUtils.clearTimeComponent(date));


        // now iterate through the relationships and fetch the patients
        Set<Patient> patients = new HashSet<Patient>();
        for (Relationship relationship : relationships) {

            if (!relationship.getPersonB().isPatient()) {
                throw new APIException("Invalid relationship " + relationship + ": person b must be a patient");
            }

            Patient p = Context.getPatientService().getPatient(relationship.getPersonB().getId());
            if (!p.isVoided()) {
                patients.add(Context.getPatientService().getPatient(relationship.getPersonB().getId()));
            }
        }

        return new ArrayList<Patient>(patients);
    }

    @Override
    public List<Patient> getPatients(Person provider, RelationshipType relationshipType)
            throws PersonIsNotProviderException, InvalidRelationshipTypeException {
        return getPatients(provider, relationshipType, new Date());
    }

    @Override
    public List<Patient> getPatients(Person provider, Date date)
            throws PersonIsNotProviderException {

        if (provider == null) {
            throw new APIException("Provider cannot be null");
        }

        Set<Patient> patients = new HashSet<Patient>();
        for (RelationshipType relationshipType : getAllProviderRoleRelationshipTypes()) {
            try {
                patients.addAll(getPatients(provider, relationshipType, date));
            }
            catch (InvalidRelationshipTypeException e) {
                // we should never get this exception, since getAlProviderRoleRelationshipTypes
                // should only return valid relationship types; so if we do get this exception, throw a runtime exception
                // instead of forcing calling methods to catch it
                throw new APIException(e);
            }
        }

        return new ArrayList<Patient>(patients);
    }

    @Override
    public List<Patient> getPatients(Person provider)
            throws PersonIsNotProviderException {
        return getPatients(provider, new Date());
    }


    @Override
    public void transferAllPatients(Person sourceProvider, Person destinationProvider, RelationshipType relationshipType)
        throws ProviderDoesNotSupportRelationshipTypeException, SourceProviderSameAsDestinationProviderException,
        PersonIsNotProviderException, InvalidRelationshipTypeException {

        if (sourceProvider == null) {
            throw new APIException("Source provider cannot be null");
        }

        if (destinationProvider == null) {
            throw new APIException("Destination provider cannot be null");
        }

        if (!ProviderManagementUtils.isProvider(sourceProvider)) {
            throw new PersonIsNotProviderException(sourceProvider + " is not a provider");
        }

        if (!ProviderManagementUtils.isProvider(destinationProvider)) {
            throw new PersonIsNotProviderException(destinationProvider + " is not a provider");
        }

        if (sourceProvider.equals(destinationProvider)) {
            throw new SourceProviderSameAsDestinationProviderException("Provider " + sourceProvider + " is the same as provider " + destinationProvider);
        }

        if (relationshipType == null) {
            throw new APIException("Relationship type cannot be null");
        }
        
       // first get all the patients of the source provider
       List<Patient> patients = getPatients(sourceProvider, relationshipType);

       // assign these patients to the new provider, unassign them from the old provider
        for (Patient patient : patients) {
            try {
                assignPatientToProvider(patient, destinationProvider, relationshipType);
            }
            catch (PatientAlreadyAssignedToProviderException e) {
                // we can ignore this exception; no need to assign patient if already assigned
            }
            try {
                unassignPatientFromProvider(patient, sourceProvider, relationshipType);
            }
            catch (PatientNotAssignedToProviderException e) {
                // we should fail hard here, because getPatients should only return patients of the provider,
                // so if this exception has been thrown, something has gone really wrong
                throw new APIException("All patients here should be assigned to provider,", e);
            }
        }
    }
    
    @Override
    public void transferAllPatients(Person sourceProvider, Person destinationProvider)
            throws ProviderDoesNotSupportRelationshipTypeException, PersonIsNotProviderException,
            SourceProviderSameAsDestinationProviderException {
        for (RelationshipType relationshipType : getAllProviderRoleRelationshipTypes()) {
            try {
                transferAllPatients(sourceProvider, destinationProvider, relationshipType);
            }
            catch (InvalidRelationshipTypeException e) {
                // we should never get this exception, since getAlProviderRoleRelationshipTypes
                // should only return valid relationship types; so if we do get this exception, throw a runtime exception
                // instead of forcing calling methods to catch it
                throw new APIException(e);
            }
        }
    }

    /**
     * Utility methods
     */
    private List<Person> providersToPersons(List<Provider> providers) {
        
        if (providers == null) {
            return null;
        }
        
        Set<Person> persons = new HashSet<Person>();
        
        for (Provider provider : providers) {
            persons.add(provider.getPerson());
        }

        return new ArrayList<Person>(persons);
    }
}
