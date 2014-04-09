package org.sharegov.cirm.rdb;

import org.hypergraphdb.util.Pair;
import org.semanticweb.owlapi.model.OWLEntity;

public class DbId extends Pair<Long, OWLEntity>
{
    private static final long serialVersionUID = 1L;

    private Boolean existing;
    
    public DbId(Long id, OWLEntity e, Boolean existing)
    {
        super(id, e);
        this.existing = existing;
    }
 
    public boolean isNew() 
    { 
        if (existing == null)
            throw new UnsupportedOperationException("It is not known whether entity : " 
                    + getSecond() + " with ID " + getFirst() + " is already stored in the database.");
        return !existing;
    }
    
    public boolean isExisting()
    {
        if (existing == null)
            throw new UnsupportedOperationException("It is not known whether entity : " 
                    + getSecond() + " with ID " + getFirst() + " is already stored in the database.");
        return existing;        
    }
    
    public boolean isKnown()
    {
        return existing != null;
    }
}