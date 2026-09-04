package com.codinglair.taf.api.soap;

import com.codinglair.taf.runtime.core.controller.TestController;

public interface SoapController extends TestController {
  SoapResponse exchange(SoapRequest request);
}
