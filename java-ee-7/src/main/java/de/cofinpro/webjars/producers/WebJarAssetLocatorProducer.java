package de.cofinpro.webjars.producers;

import org.webjars.WebJarAssetLocator;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

/**
 * Created by David
 * Date: 01.05.2018 - 16:03.
 */
public class WebJarAssetLocatorProducer {
    @Produces @ApplicationScoped
    public WebJarAssetLocator webJarAssetLocator() {
        return new WebJarAssetLocator();
    }
}
