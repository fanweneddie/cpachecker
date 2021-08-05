// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2018 Lokesh Nandanwar
// SPDX-FileCopyrightText: 2018-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

describe("ReportController", function () {
  let $rootScope;
  let $scope;
  let controller;

  beforeEach(function () {
    angular.mock.module("report");

    angular.mock.inject(function ($injector) {
      $rootScope = $injector.get("$rootScope");
      $scope = $rootScope.$new();
      controller = $injector.get("$controller")("ErrorpathController", {
        $scope: $scope,
      });
    });
    jasmine.getFixtures().fixturesPath = "base/";
    jasmine.getFixtures().load("testReport.html");
  });

  describe("errorPath initialization", function () {
    it("Should instantiate errorPath", function () {
      expect($scope.errorPath).not.toBeUndefined();
    });
  });

  describe("errPathPrevClicked action handler", function () {
    it("Should instantiate errPathPrevClicked", function () {
      expect($scope.errPathPrevClicked).not.toBeUndefined();
    });
  });

  describe("errPathNextClicked action handler", function () {
    it("Should instantiate errPathNextClicked", function () {
      expect($scope.errPathNextClicked).not.toBeUndefined();
    });
  });

  describe("errPathStartClicked action handler", function () {
    it("Should instantiate errPathStartClicked", function () {
      expect($scope.errPathStartClicked).not.toBeUndefined();
    });
  });

  describe("clickedErrpathElement action handler", function () {
    it("Should instantiate clickedErrpathElement", function () {
      expect($scope.clickedErrpathElement).not.toBeUndefined();
    });
  });
});
