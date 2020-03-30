import React, {Component} from 'react';
import {Button, Dropdown} from 'semantic-ui-react';
import {Line} from 'react-chartjs-2';

import Admin from './Admin.js'

import 'chartjs-plugin-colorschemes';
import './style.css';

const api = '/api/';

export default class App extends Component {
    chartRef = React.createRef();
    state = {
        hasPlotted: false,
        forceAxesUpdate: false,
        prePlotError: null,
        isFetching: false,
        isLoading: false,
        loadedSources: [],
        availableSources: [],
        selectedSourcesLeft: [],
        selectedSourcesRight: [],
        retentionMinutes: 60,
        loadedRetentionMinutes: 60,
        datasets: [],
        scaleModifier: 1,
    };

    componentDidUpdate = () => {
        const {yAxes, forceAxesUpdate} = this.state;
        const chart = this.chartRef.current.chartInstance;

        if (forceAxesUpdate && chart != null) {
            chart.options.scales.yAxes = yAxes;
            chart.update();
            this.setState({forceAxesUpdate: false});
        }
    };

    componentWillUnmount = () => {
        clearInterval(this.interval);
    };

    componentDidMount = () => {
        this.interval = setInterval(() => {
            this.loadData(true);
            this.fetchSources(true);
        }, 3 * 1000);

        const chartContainer = document.getElementById('chart-wrapper');
        const dataCanvas = document.getElementById('data-chart');
        const cursorCanvas = document.getElementById('cursor');
        const cursorCtx = cursorCanvas.getContext('2d');

        chartContainer.onmousemove = (e) => {
            const dataWidth = parseInt(dataCanvas.style.width, 10);
            const dataHeight = parseInt(dataCanvas.style.height, 10);
            cursorCanvas.width = dataWidth;
            cursorCanvas.height = dataHeight;
            const {offsetLeft, offsetTop} = cursorCanvas;
            const {clientX, clientY} = e;
            cursorCtx.clearRect(0, 0, cursorCanvas.width, cursorCanvas.height);
            if (clientX <= offsetLeft + dataWidth && clientX >= 48 && clientY <= offsetTop + dataHeight && clientY >= offsetTop) {
                cursorCtx.beginPath();
                cursorCtx.moveTo(clientX - offsetLeft, this.state.datasets.length > 0 ? 32 : 10);
                cursorCtx.lineTo(clientX - offsetLeft, dataHeight - 30);
                cursorCtx.stroke();
            }
        };
        chartContainer.onmouseleave = () => {
            cursorCtx.clearRect(0, 0, cursorCanvas.width, cursorCanvas.height);
        };

        this.fetchSources(false);
    };

    createSource = (data) => {
        console.log("creating source " + data);

    };

    updateSource = (id, data) => {
        console.log("update source " + id + ": " + data);

    };

    deleteSource = (id, callback) => {
        console.log("deleting source " + id);
        fetch(`${api}source/${id}`, {
            method: 'delete'
        }).then(() => {
            this.fetchSources(true).then(() => callback(true));
        }).catch(() => {
            this.fetchSources(true).then(() => callback(false));
        });

    };

    fetchSources = (background) => {
        const {selectedSourcesLeft, selectedSourcesRight} = this.state;

        if (!background) {
            this.setState({isFetching: true});
        }
        return fetch(`${api}source`)
            .then(response => {
                return response.json();
            })
            .then(results => {
                const loaded = results.flatMap(source =>
                    source.dataLabels.map(dataLabel => ({
                        source: source,
                        dataLabel: dataLabel
                    }))
                );
                const sources = Object.assign({}, ...loaded.map(entry => {
                    const source = entry.source;
                    const dataLabel = entry.dataLabel;
                    const id = source.url + ':' + dataLabel;
                    return {
                        [id]: {
                            selected: false,
                            key: id,
                            dataLabel: dataLabel,
                            value: id,
                            text: source.name + ' [' + dataLabel + ']',
                            source: source
                        }
                    }
                }));
                this.setState({
                    loadedSources: results,
                    availableSources: sources,
                    selectedSourcesLeft: selectedSourcesLeft.filter(source => source in sources),
                    selectedSourcesRight: selectedSourcesRight.filter(source => source in sources)
                });
                if (!background) {
                    this.setState({isFetching: false});
                }
            })
            .catch((reason) => {
                console.log(reason);
            });
    };

    loadData = (background) => {
        const {selectedSourcesLeft, selectedSourcesRight, retentionMinutes, availableSources, prePlotError, hasPlotted} = this.state;

        if (!background || hasPlotted) {
            if (prePlotError == null) {
                const aggIntervals = [
                    {
                        geq: 6 * 30 * 24 * 60,
                        interval: 'week'
                    },
                    {
                        geq: 30 * 24 * 60,
                        interval: 'day'
                    },
                    {
                        geq: 7 * 24 * 60,
                        interval: 'hour'
                    },
                    {
                        geq: 0,
                        interval: 'minute'
                    }
                ];

                const interval = aggIntervals.find(cfg => retentionMinutes >= cfg.geq).interval;

                if (!background) {
                    this.setState({isLoading: true, hasPlotted: true});
                }
                Promise.all(
                    selectedSourcesLeft.concat(selectedSourcesRight).map(sourceId =>
                        fetch(`${api}query?source=${sourceId}&from=${Date.now() - retentionMinutes * 60 * 1000}&to=${Date.now()}&aggregation=average&interval=${interval}`)
                            .then(response => response.json())
                            .then(results => {
                                const sourceRef = availableSources[sourceId];
                                return {
                                    label: sourceRef.text,
                                    yAxisID: selectedSourcesRight.includes(sourceRef.key) ? 'y-axis-r' : 'y-axis-l',
                                    source: sourceRef,
                                    fill: false,
                                    borderJoinStyle: 'round',
                                    lineTension: 0,
                                    pointRadius: 3,
                                    pointHoverRadius: 6,
                                    data: results.map(source => {
                                        return {
                                            x: source.timestamp,
                                            y: source.value
                                        };
                                    })
                                };
                            })
                    )
                ).then(results => {
                    if (!background) {
                        this.setState({isLoading: false});
                    }
                    this.setState({
                        datasets: results,
                        loadedRetentionMinutes: retentionMinutes
                    });
                }).catch((reason) => {
                    console.log(reason);
                });
            }
        }
    };

    handleRangeSelect = (event, data) => {
        this.setState({retentionMinutes: data.value});
    };

    handleLeftSourceSelect = (event, data) => {
        this.setState({selectedSourcesLeft: data.value, hasPlotted: false});
    };

    handleRightSourceSelect = (event, data) => {
        this.setState({selectedSourcesRight: data.value, hasPlotted: false});
    };

    handleWheel = (e) => {
        const scaleModifier = Math.max(1, this.state.scaleModifier + e.deltaY / 5000.0);
        this.setState({
            scaleModifier: scaleModifier
        });
    };

    render() {
        const {selectedSourcesLeft, selectedSourcesRight, isFetching, isLoading, availableSources, loadedSources, datasets, loadedRetentionMinutes, prePlotError} = this.state;

        const dateFormats = [
            {
                geq: 6 * 30 * 24 * 60,
                unit: 'month'
            },
            {
                geq: 3 * 24 * 60,
                unit: 'day'
            },
            {
                geq: 0,
                unit: 'hour'
            }
        ];

        const format = dateFormats.find(cfg => loadedRetentionMinutes >= cfg.geq);

        const customFormat = ('formatStr' in format) ? {[format.unit]: format.formatStr} : {};

        const options = {
            plugins: {
                colorschemes: {
                    scheme: 'office.Berlin6'
                }
            },
            responsive: true,
            tooltips: {
                mode: 'nearest',
                intersect: false,
                axis: 'x'
            },
            hover: {
                mode: 'nearest',
                intersect: false,
                axis: 'x'
            },
            scales: {
                yAxes: [
                    {
                        id: 'y-axis-l',
                        position: 'left'
                    },
                    {
                        id: 'y-axis-r',
                        position: 'right'
                    }
                ],
                xAxes: [
                    {
                        type: 'time',
                        bounds: 'ticks',
                        time: {
                            displayFormats: customFormat,
                            unit: format.unit
                        },
                        ticks: {
                            maxTicksLimit: 12,
                            autoSkip: true,
                            min: Date.now() - (loadedRetentionMinutes * 60 * 1000),
                            max: Date.now()
                        },
                        distribution: 'linear'
                    }
                ]
            }
        };

        const ranges = [
            {
                value: 60,
                text: 'Past hour'
            },
            {
                value: 6 * 60,
                text: 'Past 6 hours'
            },
            {
                value: 24 * 60,
                text: 'Past day'
            },
            {
                value: 7 * 24 * 60,
                text: 'Past week'
            },
            {
                value: 31 * 24 * 60,
                text: 'Past month'
            },
            {
                value: 365 * 24 * 60,
                text: 'Past year'
            }
        ];

        return (
            <div className='container'>
                <span className='datasource-select'>
                    <Dropdown
                        placeholder='Left axis data source'
                        fluid
                        multiple
                        search
                        selection
                        loading={isFetching || isLoading}
                        disabled={isFetching || isLoading}
                        options={Object.values(availableSources).filter(src => !selectedSourcesRight.includes(src.key))}
                        onChange={this.handleLeftSourceSelect}
                    />
                    <Dropdown
                        placeholder='Right axis data source'
                        fluid
                        multiple
                        search
                        selection
                        loading={isFetching || isLoading}
                        disabled={isFetching || isLoading}
                        options={Object.values(availableSources).filter(src => !selectedSourcesLeft.includes(src.key))}
                        onChange={this.handleRightSourceSelect}
                    />
                </span>
                <span className='range-select'>
                    <Dropdown
                        placeholder='Range'
                        fluid
                        selection
                        loading={isLoading}
                        disabled={isLoading}
                        options={ranges}
                        onChange={this.handleRangeSelect}
                        defaultValue={ranges[0].value}
                    />
                </span>
                <span className='plot-button'>
                    <Button
                        loading={isFetching || isLoading}
                        disabled={isFetching || isLoading || prePlotError != null}
                        onClick={() => this.loadData(false)}
                    >
                        Plot
                    </Button>
                </span>
                <span className='info-text'>
                    {prePlotError}
                </span>

                <span className='admin-launcher'>
                    <Admin sources={loadedSources}
                           onUpdate={(id, data) => this.updateSource(id, data)}
                           onDelete={(id, callback) => this.deleteSource(id, callback)}
                           onCreate={(data) => this.createSource(data)}
                    />
                </span>
                <br style={{clear: 'both'}}/>
                <div className='chart-container' id='chart-wrapper' onWheel={(e) => this.handleWheel(e)}>
                    <canvas id='cursor'/>
                    <Line ref={this.chartRef} id='data-chart' data={{datasets: datasets}} options={options} redraw={false}/>
                </div>
            </div>
        );
    }
}
