package com.l7tech.custom.assertions.injectionfilter;


import org.springframework.transaction.annotation.Transactional;

public interface InjectionFilterCustomExtensionInterface {

    @Transactional
    void loadPreDefinedFilters();

}
