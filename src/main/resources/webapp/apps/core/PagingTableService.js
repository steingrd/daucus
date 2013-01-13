function PagingTableService() {
  var create = function ($scope, fetchCallback, options) {
    options = options || {};
    var watcher = options.watcher || function(){};
    var self = {
      rows: [],
      query: "",
      startIndex: options.startIndex || 0,
      count: options.count || 10,
      currentlySearching: false
    };

    var update = function(){
      self.currentlySearching = true;
      fetchCallback(self.startIndex, self.count, self.query, function(data) {
        self.rows = data.rows;
        watcher();
        self.currentlySearching = false;
      });
    };

    self.first = function () {
      self.startIndex = 0;
      update();
    };

    self.next = function () {
      if (self.currentlySearching) {
        return;
      }
      self.startIndex += self.count;
      update();
    };

    self.prev = function () {
      if (self.currentlySearching) {
        return;
      }
      if (self.startIndex == 0) {
        return;
      }
      self.startIndex -= self.count;
      update();
    };

    /*
     * The search functions needs to know if there already is a search in progress and if so, do not send the search
     * before the previous one completes.
     */

    self.onSearch = function () {
      update();
    };

    self.onSearchChange = function () {
      update();
    };

    self.showPrev = function () {
      return self.startIndex > 0;
    };

    self.showNext = function () {
      return true;
    };

    self.nextDisabled = function () {
      return self.currentlySearching;
    };

    self.prevDisabled = function () {
      return self.currentlySearching;
    };

    // Do an initial fetch
    update();

    return self;
  };

  var defaultCallback = function(Resource, args) {
    args = args || {};
    return function(startIndex, count, query, cb) {
      if(startIndex || startIndex == 0) {
        args.startIndex = startIndex;
      }
      if(count) {
        args.count = count;
      }
      if(query) {
        args.query = query;
      }
      console.log("Fetching page. args =", args);
      Resource.query(args, function(data, headers) {
        var totalResults = headers("total-results");
        console.log("Total results =", totalResults, "Data =", data);
        cb({
          totalResults: totalResults,
          rows: data
        });
      });
    };
  };

  return {
    create: create,
    defaultCallback: defaultCallback
  }
}

angular.
    module('pagingTableService', ['ngResource']).
    factory('PagingTableService', PagingTableService);
