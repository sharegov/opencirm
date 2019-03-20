/*******************************************************************************
 * Copyright 2014 Miami-Dade County
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.sharegov.cirm.user;

import java.util.Date;

import mjson.Json;

import org.sharegov.cirm.utils.ThreadLocalStopwatch;

/**
 * Thread safe simple FailoverUserProvider Implementation.
 * 
 * Uses originalUP User Provider until failure and then replicateUP for a
 * duration of DEFAULT_FALLBACK_SECS (or specified) before trying originalUP
 * again. If fallBackSecs is set to zero, the originalUP will be tried for each
 * query before the replicateUP even after failure.
 * 
 * 
 * It makes no assumption that userProvider and replicateUP return equal
 * results.
 * 
 * @author Thomas Hilpold
 * 
 */
public class FailoverUserProvider implements UserProvider
{
    public static boolean DBG = true;

    public static int DEFAULT_FALLBACK_SECS = 30; // use failover before retry
                                                  // original secs

    private volatile UserProvider originalUP; // null allowed

    private volatile UserProvider replicateUP; // null allowed

    private volatile Date lastFailureTime = new Date(0);

    private volatile UserProvider activeUP = null; // must use accessor method
                                                   // (thread safety)

    /**
     * Time to use failover before checking userProvider again.
     */
    private volatile int fallbackSecs;

    /**
     * Creates a Failoveruserprovider with DEFAULT_FALLBACK_SECS. Null allowed
     * for one UP, but not both.
     * 
     * @param userProvider
     *            the original user provider
     * @param replicateUP
     *            a replicate of the original to use, when original fails.
     */
    public FailoverUserProvider(UserProvider originalUP,
            UserProvider replicateUP)
    {
        this(originalUP, replicateUP, DEFAULT_FALLBACK_SECS);
    }

    /**
     * Creates a Failoveruserprovider. Null allowed for one UP, but not both.
     * 
     * @param userProvider
     *            the original user provider
     * @param replicateUP
     *            a replicate of the original to use, when original fails.
     * @param useFailoverSecs
     *            use 0 to always try userP first
     */
    public FailoverUserProvider(UserProvider originalUP,
            UserProvider replicateUP, int fallbackSecs)
    {
        if (originalUP == null && replicateUP == null)
            throw new IllegalArgumentException(
                    "Initialized with two NULL providers. Giving up.");
        if (originalUP == null)
            System.err
                    .println("FailoverUserProvider: Initialized with null original. Using only replicate.");
        if (replicateUP == null)
            System.err
                    .println("FailoverUserProvider: Initialized with null replicate. Using only original.");
        if (fallbackSecs < 0)
            throw new IllegalArgumentException(
                    "fallbackSecs < 0; 0 or greater allowed.");
        this.originalUP = originalUP;
        this.replicateUP = replicateUP;
        this.fallbackSecs = fallbackSecs;
        if (DBG)
            ThreadLocalStopwatch.getWatch().time(
                    "FailOverUserProvider: Initialized (Fallback secs: "
                            + fallbackSecs + ")");
    }

    public boolean authenticate(String username, String password)
    {
        try
        {
            return getActiveUserProvider().authenticate(username, password);
        }
        catch (Exception e)
        {
            failed(e);
            return replicateUP.authenticate(username, password);
        }
    }

    @Override
    public Json find(String attribute, String value)
    {
        try
        {
            return getActiveUserProvider().find(attribute, value);
        }
        catch (Exception e)
        {
            failed(e);
            return replicateUP.find(attribute, value);
        }
    }

    @Override
    public Json find(Json prototype)
    {
        try
        {
            return getActiveUserProvider().find(prototype);
        }
        catch (Exception e)
        {
            failed(e);
            return replicateUP.find(prototype);
        }
    }

    @Override
    public Json find(Json prototype, int resultLimit)
    {
        try
        {
            return getActiveUserProvider().find(prototype, resultLimit);
        }
        catch (Exception e)
        {
            failed(e);
            return replicateUP.find(prototype, resultLimit);
        }
    }

    public Json findGroups(String id)
    {
        try
        {
            return getActiveUserProvider().findGroups(id);
        }
        catch (Exception e)
        {
            failed(e);
            return replicateUP.findGroups(id);
        }
    }
    
    @Override
    public Json get(String id)
    {
        try
        {
            return getActiveUserProvider().get(id);
        }
        catch (Exception e)
        {
            failed(e);
            return replicateUP.get(id);
        }
    }

    public Json populate(Json user)
    {
    	try
    	{
    		return getActiveUserProvider().populate(user);
    	}
    	catch (Exception e)
    	{
    		failed(e);
    		return replicateUP.populate(user);
    	}
    }

    @Override
    public String getIdAttribute()
    {
        try
        {
            return getActiveUserProvider().getIdAttribute();
        }
        catch (Exception e)
        {
            failed(e);
            return replicateUP.getIdAttribute();
        }
    }

    protected UserProvider getActiveUserProvider()
    {
        UserProvider nextUP; // thread safety
        if (originalUP == null)
        {
            if (DBG)
                ThreadLocalStopwatch
                        .getWatch()
                        .time("FailOverUserProvider: original null, only using replicate.");
            nextUP = replicateUP;
        }
        else if (replicateUP == null)
        {
            if (DBG)
                ThreadLocalStopwatch
                        .getWatch()
                        .time("FailOverUserProvider: replicate null, only using original.");
            nextUP = originalUP;
        }
        else
        {
            // Use replicate if original failed until fallback over and next
            // failure
            UserProvider lastUP = activeUP;
            nextUP = isFallBackNow() ? originalUP : replicateUP;
            activeUP = nextUP;
            if (DBG && lastUP == replicateUP && nextUP == originalUP)
                ThreadLocalStopwatch.getWatch().time(
                        "FailOverUserProvider: Falling back to originalUP");
            else if (DBG && (lastUP == null || lastUP == originalUP)
                    && nextUP == replicateUP)
                ThreadLocalStopwatch.getWatch().time(
                        "FailOverUserProvider: Using replicateUP");
        }
        return nextUP;
    }

    /**
     * If the fallback time passed since last failure, use original again (fall
     * back).
     * 
     * @return
     */
    protected boolean isFallBackNow()
    {
        return fallbackSecs == 0
                || (new Date().getTime() - lastFailureTime.getTime() > fallbackSecs * 1000L);
    }

    protected void failed(Exception e)
    {
        if (DBG)
            ThreadLocalStopwatch.getWatch().time(
                    "FailOverUserProvider: original failed, trying replicate. Error was: "
                            + e);
        lastFailureTime = new Date();
    }

    public UserProvider getOriginalUP()
    {
        return originalUP;
    }

    public void setOriginalUP(UserProvider originalUP)
    {
        this.originalUP = originalUP;
    }

    public UserProvider getReplicateUP()
    {
        return replicateUP;
    }

    public void setReplicateUP(UserProvider replicateUP)
    {
        this.replicateUP = replicateUP;
    }

    public int getFallbackSecs()
    {
        return fallbackSecs;
    }

    public void setFallbackSecs(int fallbackSecs)
    {
        this.fallbackSecs = fallbackSecs;
    }

    /**
     * @return date with time 0, if not yet failed.
     */
    public Date getLastFailureTime()
    {
        return lastFailureTime;
    }
}
