(function () {
  'use strict';

  const estado = {
    token: localStorage.getItem('torven_token'),
    usuario: JSON.parse(localStorage.getItem('torven_usuario') || 'null'),
    distritos: [],
    carrito: [] // { idProducto, nombre, precio, stock, cantidad }
  };

  const el = (id) => document.getElementById(id);

  const pantallaLogin = el('pantalla-login');
  const pantallaVenta = el('pantalla-venta');
  const pantallaConfirmacion = el('pantalla-confirmacion');
  const pantallaDetalleReserva = el('pantalla-detalle-reserva');

  // ---------- Arranque ----------

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js').catch(function () {
      // Si falla el service worker la app sigue funcionando, solo sin cache.
    });
  }

  // La decision de que pantalla mostrar al abrir se dispara al final de este
  // archivo (ver "Arranque final"), no aqui arriba. Si se llama aqui, con una
  // sesion ya guardada esto invoca mostrarPantallaVenta() -> funciones que
  // leen variables "let" (distritosCargados, categoriasGastoCargadas, etc.)
  // declaradas mas abajo en el archivo, que todavia estan en su "zona muerta
  // temporal": el motor de JS lanza un ReferenceError y todo el script se
  // detiene ahi mismo, sin llegar a registrar ningun boton de la pantalla de
  // ventas/entregas/gastos. Eso deja la app viendose cargada pero sin
  // responder a ningun toque, indistinguible de una pantalla "congelada" -
  // y pasa justo en el caso mas comun: abrir la PWA con una sesion que ya
  // habia iniciado sesion antes.

  // ---------- Navegacion entre pantallas ----------

  function mostrarPantalla(pantalla) {
    [pantallaLogin, pantallaVenta, pantallaConfirmacion, pantallaDetalleReserva].forEach(function (p) {
      p.classList.toggle('oculto', p !== pantalla);
    });
  }

  function mostrarPantallaVenta(opciones) {
    el('nombre-usuario').textContent = estado.usuario.nombre || estado.usuario.usuario;
    el('rol-usuario').textContent = estado.usuario.rol || '';
    mostrarPantalla(pantallaVenta);
    cargarCategoriasGastoSiHaceFalta();
    cargarDistritosSiHaceFalta().then(function () {
      if (opciones && opciones.restaurarBorrador) {
        restaurarBorrador();
      }
    });
    reiniciarTemporizadorInactividad();
  }

  // ---------- Recuperacion tras segundo plano (pantalla "congelada") ----------
  //
  // Android y iOS pueden congelar o directamente matar la pestana de la PWA
  // cuando pasa un rato en segundo plano (para ahorrar bateria/memoria). Al
  // volver, Chrome a veces solo muestra una captura vieja de la pantalla que
  // ya no responde a nada (bug conocido de Chrome/WebAPK al "despertar" una
  // pestana). No hay forma confiable de detectar esto desde JS y "reparar"
  // la pagina en el sitio: lo unico que garantiza que la app vuelva a
  // responder siempre es recargarla por completo. Para que eso no se sienta
  // como perder el trabajo a medias, el carrito y los datos de entrega se
  // guardan justo antes de pasar a segundo plano y se restauran solos al
  // volver a cargar.

  const BORRADOR_KEY = 'torven_borrador_venta';
  const UMBRAL_RECARGA_MS = 2000;
  let vistaActivaActual = 'ventas';
  let ultimaVezOculto = null;

  function guardarBorrador() {
    if (!estado.token) {
      return;
    }
    try {
      sessionStorage.setItem(BORRADOR_KEY, JSON.stringify({
        vista: vistaActivaActual,
        carrito: estado.carrito,
        direccion: el('entrega-direccion').value,
        distrito: selectDistrito.value,
        numero: el('entrega-numero').value
      }));
    } catch (error) {
      // sessionStorage puede fallar sin espacio o en navegacion privada: no es critico.
    }
  }

  function limpiarBorrador() {
    try {
      sessionStorage.removeItem(BORRADOR_KEY);
    } catch (error) {
      // no es critico
    }
  }

  function restaurarBorrador() {
    let borrador = null;
    try {
      borrador = JSON.parse(sessionStorage.getItem(BORRADOR_KEY) || 'null');
    } catch (error) {
      borrador = null;
    }
    if (!borrador) {
      return;
    }
    estado.carrito = Array.isArray(borrador.carrito) ? borrador.carrito : [];
    actualizarCarritoUI();
    if (borrador.direccion) {
      el('entrega-direccion').value = borrador.direccion;
    }
    if (borrador.numero) {
      el('entrega-numero').value = borrador.numero;
    }
    if (borrador.distrito) {
      selectDistrito.value = borrador.distrito;
    }
    actualizarBotonConfirmar();
    if (borrador.vista && borrador.vista !== 'ventas') {
      mostrarVistaPrincipal(borrador.vista);
    }
  }

  function manejarRegresoAPrimerPlano() {
    if (!estado.token) {
      return;
    }
    const estuvoOcultaBastante = ultimaVezOculto !== null && (Date.now() - ultimaVezOculto) >= UMBRAL_RECARGA_MS;
    ultimaVezOculto = null;
    if (estuvoOcultaBastante) {
      location.reload();
      return;
    }
    // Oculta muy poco tiempo (cambio de pantalla accidental, gesto del
    // sistema): no hace falta recargar, solo refrescar lo que pueda haber
    // cambiado y reiniciar el cierre de sesion por inactividad.
    reiniciarTemporizadorInactividad();
    if (vistaActivaActual === 'entregas') {
      cargarReservas();
    } else if (vistaActivaActual === 'gastos') {
      cargarGastosHoy();
    }
  }

  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'hidden') {
      ultimaVezOculto = Date.now();
      guardarBorrador();
    } else {
      manejarRegresoAPrimerPlano();
    }
  });

  // Respaldo para cuando visibilitychange no alcanza a dispararse antes de
  // que el sistema suspenda la pagina (mas comun en iOS), y para navegadores
  // que si implementan la Page Lifecycle API (freeze/resume).
  window.addEventListener('pagehide', guardarBorrador);
  document.addEventListener('freeze', guardarBorrador);
  document.addEventListener('resume', manejarRegresoAPrimerPlano);
  window.addEventListener('pageshow', function (evento) {
    if (evento.persisted) {
      manejarRegresoAPrimerPlano();
    }
  });

  // ---------- Cierre de sesion por inactividad ----------

  const INACTIVIDAD_LIMITE_MS = 15 * 60 * 1000;
  let temporizadorInactividad = null;

  function reiniciarTemporizadorInactividad() {
    clearTimeout(temporizadorInactividad);
    if (!estado.token) {
      return;
    }
    temporizadorInactividad = setTimeout(function () {
      cerrarSesion();
      el('error-login').textContent = 'Se cerro la sesion por inactividad.';
    }, INACTIVIDAD_LIMITE_MS);
  }

  ['click', 'touchstart', 'keydown', 'input'].forEach(function (tipoEvento) {
    document.addEventListener(tipoEvento, reiniciarTemporizadorInactividad, { passive: true });
  });

  // ---------- Llamadas a la API ----------

  const API_TIMEOUT_MS = 20 * 1000;

  function apiFetch(ruta, opciones) {
    opciones = opciones || {};
    opciones.headers = Object.assign({}, opciones.headers, {
      'Authorization': 'Bearer ' + estado.token
    });

    const controlador = new AbortController();
    opciones.signal = controlador.signal;
    const idTimeout = setTimeout(function () { controlador.abort(); }, API_TIMEOUT_MS);

    return fetch(ruta, opciones).then(function (respuesta) {
      clearTimeout(idTimeout);
      if (respuesta.status === 401) {
        cerrarSesion();
        throw new Error('Tu sesion expiro. Inicia sesion de nuevo.');
      }
      return respuesta.json().then(function (cuerpo) {
        if (!respuesta.ok) {
          throw new Error(cuerpo.error || 'Ocurrio un error.');
        }
        return cuerpo;
      });
    }).catch(function (error) {
      clearTimeout(idTimeout);
      if (error.name === 'AbortError') {
        throw new Error('La conexion tardo demasiado. Revisa tu internet e intenta de nuevo.');
      }
      throw error;
    });
  }

  // ---------- Login ----------

  el('form-login').addEventListener('submit', function (evento) {
    evento.preventDefault();
    const usuario = el('campo-usuario').value.trim();
    const clave = el('campo-clave').value;
    const errorLogin = el('error-login');
    errorLogin.textContent = '';

    fetch('/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ usuario: usuario, clave: clave })
    }).then(function (respuesta) {
      return respuesta.json().then(function (cuerpo) {
        if (!respuesta.ok) {
          throw new Error(cuerpo.error || 'No se pudo iniciar sesion.');
        }
        return cuerpo;
      });
    }).then(function (datos) {
      estado.token = datos.token;
      estado.usuario = datos;
      localStorage.setItem('torven_token', datos.token);
      localStorage.setItem('torven_usuario', JSON.stringify(datos));
      el('campo-clave').value = '';
      mostrarPantallaVenta();
    }).catch(function (error) {
      errorLogin.textContent = error.message;
    });
  });

  el('boton-salir').addEventListener('click', cerrarSesion);

  function cerrarSesion() {
    clearTimeout(temporizadorInactividad);
    if (estado.token) {
      fetch('/api/logout', {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + estado.token }
      }).catch(function () { /* no importa si falla, igual limpiamos local */ });
    }
    estado.token = null;
    estado.usuario = null;
    estado.carrito = [];
    localStorage.removeItem('torven_token');
    localStorage.removeItem('torven_usuario');
    limpiarBorrador();
    limpiarFormularioEntrega();
    actualizarCarritoUI();
    mostrarVistaPrincipal('ventas');
    mostrarPantalla(pantallaLogin);
  }

  // ---------- Pestanas Ventas / Entregas / Gastos ----------

  const vistaVentas = el('vista-ventas');
  const vistaEntregas = el('vista-entregas');
  const vistaGastos = el('vista-gastos');

  function mostrarVistaPrincipal(vista) {
    vistaActivaActual = vista;
    document.querySelectorAll('#tabs-principales .segmento').forEach(function (boton) {
      boton.classList.toggle('activo', boton.getAttribute('data-vista') === vista);
    });
    vistaVentas.classList.toggle('oculto', vista !== 'ventas');
    vistaEntregas.classList.toggle('oculto', vista !== 'entregas');
    vistaGastos.classList.toggle('oculto', vista !== 'gastos');
    if (vista === 'entregas') {
      cargarReservas();
    } else if (vista === 'gastos') {
      cargarGastosHoy();
    }
  }

  document.querySelectorAll('#tabs-principales .segmento').forEach(function (boton) {
    boton.addEventListener('click', function () {
      mostrarVistaPrincipal(boton.getAttribute('data-vista'));
    });
  });

  // ---------- Entregas (reservas pendientes) ----------

  const listaReservas = el('lista-reservas');
  const reservasVacio = el('reservas-vacio');
  let reservasActuales = [];

  function cargarReservas() {
    apiFetch('/api/reservas').then(function (reservas) {
      renderizarReservas(reservas);
    }).catch(function (error) {
      listaReservas.innerHTML = '';
      reservasVacio.textContent = error.message || 'No se pudieron cargar las entregas.';
      reservasVacio.classList.remove('oculto');
    });
  }

  function renderizarReservas(reservas) {
    reservasActuales = reservas;
    listaReservas.innerHTML = '';
    reservasVacio.textContent = 'No hay entregas pendientes.';
    reservasVacio.classList.toggle('oculto', reservas.length > 0);

    reservas.forEach(function (reserva) {
      const claseTiempo = claseSegunTiempoRestante(reserva.horaEntregaPactada);
      const li = document.createElement('li');
      li.className = 'tarjeta-reserva' + (claseTiempo ? ' ' + claseTiempo : '');
      li.innerHTML =
        '<div class="tarjeta-reserva-info">' +
        '  <div class="item-titulo">' + escapar(reserva.direccion) + '</div>' +
        '  <div class="item-detalle">' + escapar(reserva.distrito || '') + '</div>' +
        '</div>' +
        '<div class="tarjeta-reserva-hora">' + formatearFechaHora(reserva.horaEntregaPactada) + '</div>';
      li.addEventListener('click', function () {
        abrirDetalleReserva(reserva.idVenta);
      });
      listaReservas.appendChild(li);
    });
  }

  let idReservaAbierta = null;

  function abrirDetalleReserva(idVenta) {
    el('error-detalle-reserva').textContent = '';
    apiFetch('/api/reservas/' + idVenta).then(function (ticket) {
      idReservaAbierta = ticket.idVenta;
      el('detalle-reserva-id').textContent = ticket.idVenta;
      el('detalle-reserva-direccion').textContent = ticket.lugarEntrega.direccion || 'Sin direccion';
      el('detalle-reserva-distrito').textContent = 'Distrito: ' + (ticket.lugarEntrega.distrito || 'Sin distrito');
      el('detalle-reserva-numero').textContent = 'Telefono: ' + (ticket.lugarEntrega.numero || 'Sin telefono');
      el('detalle-reserva-hora').textContent = 'Hora de entrega pactada: ' +
        formatearFechaHora(ticket.horaEntregaPactada);

      const lista = el('detalle-reserva-lineas');
      lista.innerHTML = '';
      ticket.lineas.forEach(function (linea) {
        const li = document.createElement('li');
        const subtotal = Number(linea.precio) * linea.cantidad;
        li.innerHTML =
          '<div class="carrito-info">' +
          '  <div class="item-titulo">' + escapar(linea.nombreProducto) + '</div>' +
          '  <div class="item-detalle">S/ ' + Number(linea.precio).toFixed(2) + ' c/u &middot; x' + linea.cantidad + '</div>' +
          '</div>' +
          '<div>S/ ' + subtotal.toFixed(2) + '</div>';
        lista.appendChild(li);
      });

      el('detalle-reserva-total').textContent = 'S/ ' + Number(ticket.total).toFixed(2);
      mostrarPantalla(pantallaDetalleReserva);
    }).catch(function (error) {
      el('error-detalle-reserva').textContent = error.message || 'No se pudo abrir la reserva.';
    });
  }

  el('boton-volver-reserva').addEventListener('click', function () {
    mostrarPantalla(pantallaVenta);
  });

  function cambiarEstadoReservaAbierta(estadoNuevo) {
    if (!idReservaAbierta) {
      return;
    }
    el('error-detalle-reserva').textContent = '';
    apiFetch('/api/reservas/' + idReservaAbierta + '/estado', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ estado: estadoNuevo })
    }).then(function () {
      idReservaAbierta = null;
      mostrarPantalla(pantallaVenta);
      cargarReservas();
    }).catch(function (error) {
      el('error-detalle-reserva').textContent = error.message || 'No se pudo actualizar la reserva.';
    });
  }

  el('boton-marcar-entregada').addEventListener('click', function () {
    cambiarEstadoReservaAbierta('VENDIDA');
  });

  el('boton-cancelar-reserva').addEventListener('click', function () {
    cambiarEstadoReservaAbierta('CANCELADA');
  });

  el('boton-mapa-entregas').addEventListener('click', function () {
    abrirWazeProximaEntrega();
  });


  function abrirWazeProximaEntrega() {
    const hoy = new Date();
    const entregasHoy = reservasActuales.filter(function (reserva) {
      if (!reserva.horaEntregaPactada) {
        return false;
      }
      const fecha = new Date(reserva.horaEntregaPactada);
      return fecha.getFullYear() === hoy.getFullYear()
          && fecha.getMonth() === hoy.getMonth()
          && fecha.getDate() === hoy.getDate();
    });

    if (entregasHoy.length === 0) {
      mostrarAviso('No hay entregas pactadas para hoy.');
      return;
    }

    // Waze no soporta rutas con varias paradas via enlace directo (a
    // diferencia de Google Maps): abre navegacion a la entrega mas proxima
    // por hora pactada (reservasActuales ya viene ordenada asi desde la
    // API). Al marcarla como entregada, este boton pasa a apuntar a la
    // siguiente.
    const proxima = entregasHoy[0];
    const direccion = (proxima.direccion || '') + (proxima.distrito ? ', ' + proxima.distrito : '') + ', Lima, Peru';
    const url = 'https://waze.com/ul?q=' + encodeURIComponent(direccion) + '&navigate=yes';
    window.open(url, '_blank');
  }

  function claseSegunTiempoRestante(horaEntregaPactada) {
    if (!horaEntregaPactada) {
      return '';
    }
    const minutosRestantes = (new Date(horaEntregaPactada).getTime() - Date.now()) / 60000;
    if (minutosRestantes <= 5) {
      return 'tarjeta-reserva-rojo';
    }
    if (minutosRestantes <= 30) {
      return 'tarjeta-reserva-amarillo';
    }
    return '';
  }

  function formatearFechaHora(valor) {
    if (!valor) {
      return 'Sin definir';
    }
    const fecha = new Date(valor);
    if (isNaN(fecha.getTime())) {
      return 'Sin definir';
    }
    return fecha.toLocaleString('es-PE', {
      day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
    });
  }

  // ---------- Entrega ----------

  const selectDistrito = el('entrega-distrito');

  let distritosCargados = false;

  function cargarDistritosSiHaceFalta() {
    if (distritosCargados) {
      return Promise.resolve();
    }
    return apiFetch('/api/distritos').then(function (distritos) {
      distritosCargados = true;
      estado.distritos = distritos;
      selectDistrito.innerHTML = '';
      distritos.forEach(function (distrito) {
        const opcion = document.createElement('option');
        opcion.value = distrito.id;
        opcion.textContent = distrito.nombre;
        if (distrito.nombre === 'Otro') {
          opcion.selected = true;
        }
        selectDistrito.appendChild(opcion);
      });
    }).catch(function () {
      // Si falla, el select queda vacio; se puede reintentar mostrando la pantalla de nuevo.
    });
  }

  el('entrega-direccion').addEventListener('input', actualizarBotonConfirmar);

  function datosEntregaCompleta() {
    return el('entrega-direccion').value.trim().length > 0
        && selectDistrito.value.length > 0;
  }

  function limpiarFormularioEntrega() {
    el('entrega-direccion').value = '';
    el('entrega-numero').value = '';
  }

  // ---------- Busqueda de productos ----------

  const listaProductos = el('lista-productos');

  el('buscar-producto').addEventListener('input', debounce(function (evento) {
    const texto = evento.target.value.trim();
    apiFetch('/api/productos?buscar=' + encodeURIComponent(texto)).then(function (productos) {
      renderizarProductos(productos);
    }).catch(mostrarErrorVenta);
  }, 300));

  function renderizarProductos(productos) {
    listaProductos.innerHTML = '';
    productos.slice(0, 20).forEach(function (producto) {
      const agotado = producto.stock <= 0;
      const li = document.createElement('li');
      if (agotado) {
        li.style.opacity = '0.55';
      }
      li.innerHTML =
        '<div class="item-titulo">' + escapar(producto.nombre) + '</div>' +
        '<div class="item-detalle' + (agotado ? ' agotado' : '') + '">' +
        'S/ ' + Number(producto.precio).toFixed(2) + ' &middot; Stock: ' + producto.stock + '</div>';
      if (!agotado) {
        li.addEventListener('click', function () {
          agregarAlCarrito(producto);
          el('buscar-producto').value = '';
          listaProductos.innerHTML = '';
        });
      }
      listaProductos.appendChild(li);
    });
  }

  function agregarAlCarrito(producto) {
    const existente = estado.carrito.find(function (l) { return l.idProducto === producto.id; });
    if (existente) {
      if (existente.cantidad < producto.stock) {
        existente.cantidad += 1;
      }
    } else {
      estado.carrito.push({
        idProducto: producto.id,
        nombre: producto.nombre,
        precio: Number(producto.precio),
        stock: producto.stock,
        cantidad: 1
      });
    }
    actualizarCarritoUI();
  }

  // ---------- Carrito ----------

  const listaCarrito = el('carrito');
  const carritoVacio = el('carrito-vacio');

  function actualizarCarritoUI() {
    listaCarrito.innerHTML = '';
    carritoVacio.classList.toggle('oculto', estado.carrito.length > 0);

    let total = 0;
    estado.carrito.forEach(function (linea, indice) {
      const subtotal = linea.precio * linea.cantidad;
      total += subtotal;

      const li = document.createElement('li');
      li.innerHTML =
        '<div class="carrito-info">' +
        '  <div class="item-titulo">' + escapar(linea.nombre) + '</div>' +
        '  <div class="item-detalle">S/ ' + linea.precio.toFixed(2) + ' c/u &middot; S/ ' + subtotal.toFixed(2) + '</div>' +
        '</div>' +
        '<div class="carrito-cantidad">' +
        '  <button data-accion="menos" type="button">-</button>' +
        '  <span>' + linea.cantidad + '</span>' +
        '  <button data-accion="mas" type="button">+</button>' +
        '  <button class="carrito-quitar" data-accion="quitar" type="button">&times;</button>' +
        '</div>';

      li.querySelector('[data-accion="menos"]').addEventListener('click', function () {
        cambiarCantidad(indice, -1);
      });
      li.querySelector('[data-accion="mas"]').addEventListener('click', function () {
        cambiarCantidad(indice, 1);
      });
      li.querySelector('[data-accion="quitar"]').addEventListener('click', function () {
        estado.carrito.splice(indice, 1);
        actualizarCarritoUI();
      });

      listaCarrito.appendChild(li);
    });

    el('total-carrito').textContent = 'S/ ' + total.toFixed(2);
    actualizarBotonConfirmar();
  }

  function cambiarCantidad(indice, delta) {
    const linea = estado.carrito[indice];
    const nueva = linea.cantidad + delta;
    if (nueva <= 0) {
      estado.carrito.splice(indice, 1);
    } else if (nueva <= linea.stock) {
      linea.cantidad = nueva;
    }
    actualizarCarritoUI();
  }

  function actualizarBotonConfirmar() {
    el('boton-confirmar').disabled = !datosEntregaCompleta() || estado.carrito.length === 0;
  }

  // ---------- Confirmar venta ----------

  function crearLugarEntrega() {
    const cuerpo = {
      numero: el('entrega-numero').value.trim(),
      direccion: el('entrega-direccion').value.trim(),
      idDistrito: Number(selectDistrito.value) || 0
    };
    return apiFetch('/api/lugares-entrega', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(cuerpo)
    }).then(function (lugarEntregaCreado) {
      return lugarEntregaCreado.id;
    });
  }

  const modalHoraEntrega = el('modal-hora-entrega');
  const horaEntregaInput = el('hora-entrega-input');

  function formatoDatetimeLocal(fecha) {
    const pad = function (n) { return String(n).padStart(2, '0'); };
    return fecha.getFullYear() + '-' + pad(fecha.getMonth() + 1) + '-' + pad(fecha.getDate()) +
        'T' + pad(fecha.getHours()) + ':' + pad(fecha.getMinutes());
  }

  el('boton-confirmar').addEventListener('click', function () {
    el('error-hora-entrega').textContent = '';
    el('boton-confirmar-hora-entrega').disabled = false;
    const ahora = formatoDatetimeLocal(new Date());
    horaEntregaInput.min = ahora;
    horaEntregaInput.value = ahora;
    modalHoraEntrega.classList.remove('oculto');
  });

  el('boton-cancelar-hora-entrega').addEventListener('click', function () {
    modalHoraEntrega.classList.add('oculto');
  });

  el('boton-confirmar-hora-entrega').addEventListener('click', function () {
    const horaEntregaPactada = horaEntregaInput.value;
    if (!horaEntregaPactada) {
      el('error-hora-entrega').textContent = 'Ingresa la hora de entrega pactada.';
      return;
    }

    const boton = el('boton-confirmar-hora-entrega');
    boton.disabled = true;
    el('error-hora-entrega').textContent = '';

    crearLugarEntrega().then(function (idLugarEntrega) {
      const cuerpo = {
        idLugarEntrega: idLugarEntrega,
        horaEntregaPactada: horaEntregaPactada,
        lineas: estado.carrito.map(function (l) {
          return { idProducto: l.idProducto, cantidad: l.cantidad };
        })
      };
      return apiFetch('/api/ventas', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(cuerpo)
      });
    }).then(function (ticket) {
      modalHoraEntrega.classList.add('oculto');
      el('detalle-confirmacion').textContent =
        'Venta #' + ticket.idVenta + ' - Total S/ ' + Number(ticket.total).toFixed(2);
      estado.carrito = [];
      limpiarFormularioEntrega();
      actualizarCarritoUI();
      limpiarBorrador();
      mostrarPantalla(pantallaConfirmacion);
    }).catch(function (error) {
      el('error-hora-entrega').textContent = error.message || 'Ocurrio un error.';
      boton.disabled = false;
    });
  });

  el('boton-nueva-venta').addEventListener('click', function () {
    mostrarPantallaVenta();
  });

  function mostrarErrorVenta(error) {
    el('error-venta').textContent = error.message || 'Ocurrio un error.';
  }

  // ---------- Gastos ----------

  const selectCategoriaGasto = el('gasto-categoria');
  const listaGastos = el('lista-gastos');
  const gastosVacio = el('gastos-vacio');

  let categoriasGastoCargadas = false;

  function cargarCategoriasGastoSiHaceFalta() {
    if (categoriasGastoCargadas) {
      return;
    }
    apiFetch('/api/categorias-gasto').then(function (categorias) {
      categoriasGastoCargadas = true;
      selectCategoriaGasto.innerHTML = '';
      categorias.forEach(function (categoria) {
        const opcion = document.createElement('option');
        opcion.value = categoria.id;
        opcion.textContent = categoria.nombre;
        selectCategoriaGasto.appendChild(opcion);
      });
    }).catch(function () {
      // Si falla, el select queda vacio; se reintenta mostrando la pantalla de nuevo.
    });
  }

  function cargarGastosHoy() {
    apiFetch('/api/gastos').then(function (gastos) {
      renderizarGastos(gastos);
    }).catch(function (error) {
      listaGastos.innerHTML = '';
      gastosVacio.textContent = error.message || 'No se pudieron cargar los gastos.';
      gastosVacio.classList.remove('oculto');
    });
  }

  function renderizarGastos(gastos) {
    listaGastos.innerHTML = '';
    gastosVacio.textContent = 'Todavia no registraste gastos hoy.';
    gastosVacio.classList.toggle('oculto', gastos.length > 0);

    let total = 0;
    gastos.forEach(function (gasto) {
      total += Number(gasto.monto);
      const li = document.createElement('li');
      li.innerHTML =
        '<div class="carrito-info">' +
        '  <div class="item-titulo">' + escapar(gasto.descripcion) + '</div>' +
        '  <div class="item-detalle">' + escapar(gasto.categoria) + '</div>' +
        '</div>' +
        '<div>S/ ' + Number(gasto.monto).toFixed(2) + '</div>';
      listaGastos.appendChild(li);
    });
    el('total-gastos').textContent = 'S/ ' + total.toFixed(2);
  }

  el('boton-guardar-gasto').addEventListener('click', function () {
    const errorGasto = el('error-gasto');
    errorGasto.textContent = '';

    const idCategoriaGasto = Number(selectCategoriaGasto.value) || 0;
    const descripcion = el('gasto-descripcion').value.trim();
    const monto = Number(el('gasto-monto').value);

    if (!idCategoriaGasto) {
      errorGasto.textContent = 'Selecciona una categoria.';
      return;
    }
    if (!descripcion) {
      errorGasto.textContent = 'Ingresa una descripcion del gasto.';
      return;
    }
    if (!monto || monto <= 0) {
      errorGasto.textContent = 'Ingresa un monto valido.';
      return;
    }

    const boton = el('boton-guardar-gasto');
    boton.disabled = true;
    apiFetch('/api/gastos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idCategoriaGasto: idCategoriaGasto, descripcion: descripcion, monto: monto })
    }).then(function () {
      el('gasto-descripcion').value = '';
      el('gasto-monto').value = '';
      boton.disabled = false;
      cargarGastosHoy();
      mostrarAviso('Gasto registrado.');
    }).catch(function (error) {
      errorGasto.textContent = error.message || 'Ocurrio un error.';
      boton.disabled = false;
    });
  });

  const modalResumenMensual = el('modal-resumen-mensual');

  el('boton-resumen-mensual').addEventListener('click', function () {
    apiFetch('/api/gastos/resumen-mensual').then(function (resumen) {
      renderizarResumenMensual(resumen);
      modalResumenMensual.classList.remove('oculto');
    }).catch(function (error) {
      mostrarAviso(error.message || 'No se pudo cargar el resumen del mes.');
    });
  });

  el('boton-cerrar-resumen-mensual').addEventListener('click', function () {
    modalResumenMensual.classList.add('oculto');
  });

  function renderizarResumenMensual(resumen) {
    const lista = el('resumen-mensual-categorias');
    lista.innerHTML = '';
    resumen.porCategoria.forEach(function (fila) {
      const li = document.createElement('li');
      li.innerHTML =
        '<div class="item-titulo">' + escapar(fila.categoria) + '</div>' +
        '<div>S/ ' + Number(fila.total).toFixed(2) + '</div>';
      lista.appendChild(li);
    });
    el('resumen-mensual-total').textContent = 'S/ ' + Number(resumen.total).toFixed(2);
  }

  // ---------- Utilidades ----------

  function debounce(fn, esperaMs) {
    let temporizador;
    return function () {
      const args = arguments;
      const self = this;
      clearTimeout(temporizador);
      temporizador = setTimeout(function () { fn.apply(self, args); }, esperaMs);
    };
  }

  function escapar(texto) {
    const div = document.createElement('div');
    div.textContent = texto == null ? '' : String(texto);
    return div.innerHTML;
  }

  let toastAviso = null;
  let temporizadorAviso = null;

  function mostrarAviso(mensaje) {
    if (!toastAviso) {
      toastAviso = document.createElement('div');
      toastAviso.className = 'toast';
      document.body.appendChild(toastAviso);
    }
    toastAviso.textContent = mensaje;
    toastAviso.classList.add('visible');
    clearTimeout(temporizadorAviso);
    temporizadorAviso = setTimeout(function () {
      toastAviso.classList.remove('visible');
    }, 2600);
  }

  // ---------- Arranque final ----------
  //
  // Recien aqui, al final del archivo, ya se ejecutaron todas las
  // declaraciones "let"/"const" de arriba (distritosCargados,
  // categoriasGastoCargadas, etc.) y ya se registraron todos los
  // event listeners de botones. Es seguro decidir que pantalla mostrar.

  if (estado.token && estado.usuario) {
    mostrarPantallaVenta({ restaurarBorrador: true });
  } else {
    mostrarPantalla(pantallaLogin);
  }
})();
